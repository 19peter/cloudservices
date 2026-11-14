# Load Balancer and Auto-Scaling Groups Plan

## Goal

Represent one logical service as a group of `ComputeUnit` replicas. Route group traffic to available replicas and add replicas when the group reaches its configured capacity limit, while preserving requests that intentionally target one specific CU.

## Current state

- [`GatewayServer`](../src/main/java/org/cloudServices/servers/GatewayServer.java) interprets the first URL path segment as a CU ID or name, acquires a slot on that CU, resolves its endpoint in DNS, and proxies the request.
- `ComputeUnit` has a semaphore-backed request capacity. `ComputeUnitCapacity` currently maps MICRO/MEDIUM/LARGE/ULTRA to fixed slot counts.
- `Memdb` indexes individual CUs. There is no service group, replica set, scaling policy, or member-selection strategy.
- DNS contains endpoints for individual running/ready service instances. Keep that responsibility; do not turn DNS into a load balancer.

## Recommended route contract

Preserve the current direct route:

- `/<cu-id-or-name>/<path>` targets exactly that CU.
- If that CU is unavailable or full, return the existing capacity/unavailable response. Do not silently send this explicitly targeted request to a different CU.

Add a distinct group route:

- `/services/<service-name>/<path>` targets a logical service group.
- The gateway resolves the group, selects one eligible member, acquires that member's request slot, and resolves that member's endpoint from DNS.
- Keep the `/services/` prefix reserved so group names cannot shadow direct CU routes.

## Proposed components

### AutoScalingGroup

Owns:

- A stable logical service name.
- A workload template and CU capacity configuration used to create replicas.
- A collection of member CUs and their lifecycle states.
- A scaling policy and in-progress scale operations.

Give each member a unique CU identity/name. Do not assign the shared group name to every replica; the group name identifies the group, not an individual endpoint.

### AutoScalingGroupRegistry

Maps a logical service name to its group. Keep this separate from `Memdb`, which remains an index of individual CUs. The first implementation can be in-memory like the current registry; persistence across daemon restarts is a separate concern.

### Load-balancing selection

- Only select members that are ready and have an available request slot.
- Start with round-robin selection among eligible members. A selection result should identify the exact member whose slot was acquired, so the gateway releases the same member's slot in `finally`.
- Resolve the selected member's endpoint by its CU ID through DNS. Do not get port information from the CU or runtime.
- If a member becomes unavailable between selection and endpoint resolution, release its slot and try another eligible member within a bounded number of attempts.

### Scaling policy

Start with an understandable saturation policy:

- Scale out when every ready member is at its request-slot limit and the group has capacity below `maxReplicas`.
- Configure `minReplicas`, `maxReplicas`, `scaleOutStep`, and a cooldown.
- Count both running members and members already being created when enforcing the maximum. Use a single-flight or group-level lock so simultaneous requests do not start a storm of duplicate replicas.
- Do not scale merely because a CU is not ready; distinguish “starting/unhealthy” from “ready but saturated”.
- Defer scale-in until scale-out, routing, and draining are reliable. Later scale-in should require sustained low utilization, respect `minReplicas`, and drain a member before stopping it.

## Request behavior during scale-out

Recommended initial behavior:

1. Try ready members immediately.
2. If all are full, request one scale-out operation if policy permits.
3. Wait only for a configured, bounded interval for a new ready member.
4. If one becomes ready in time, acquire its slot and route the original request to it.
5. If the wait expires or the group is at its maximum, return `503 Service Unavailable` with `Retry-After`. Do not hold requests indefinitely.

Direct-CU requests never wait for or route to a sibling. This preserves the difference between “send to this instance” and “send to this service”.

## Implementation sequence

1. Complete the health/readiness plan first. The load balancer must not send requests to a process that has merely been launched.
2. Add group, group-member, and scaling-policy models plus an in-memory registry.
3. Add a request-slot lease/result so acquiring a slot also identifies which CU must release it.
4. Add an explicit `/services/{name}` gateway route while retaining the current direct route.
5. Implement deterministic member selection and DNS endpoint lookup by member ID.
6. Implement scale-out single-flight, pending-member accounting, cooldown, and bounded request wait.
7. Add CLI/API commands to create, inspect, and delete groups and inspect scaling activity.
8. Add scale-in only after member draining and in-flight request handling are covered.

## Verification

- Direct route tests prove a request for one CU is never routed to a sibling.
- Group route tests prove requests spread across eligible members and use DNS-resolved endpoints.
- Saturation tests prove a request causes at most one scale-out operation when concurrent requests arrive.
- Policy tests cover minimum/maximum replicas, cooldown, pending replicas, startup failure, and bounded wait timeout.
- Readiness tests prove starting or unhealthy members are never selected.
- Drain tests (for later scale-in) prove no new requests go to a draining member and active request slots are released before termination.

## Acceptance criteria

- Existing direct CU URLs continue to work with their current pinned-instance meaning.
- A logical service URL distributes requests only among ready members.
- A saturated group can add replicas within its configured bounds and route new traffic to them.
- Scale-out failure, maximum size, and timeout produce an explicit response rather than an indefinite wait.
- Every acquired request slot is released on success, proxy failure, timeout, or cancellation.

## Cross-feature notes

- Group routing depends on health/readiness: DNS should contain only traffic-eligible members.
- Individual endpoints remain in DNS; group membership belongs to the group registry.
- Docker and JAR runtimes should be interchangeable members because both return the same typed runtime submission and endpoint contract.
