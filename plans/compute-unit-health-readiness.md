# Compute Unit Health and Readiness Plan

## Goal

Distinguish “the runtime process/container was started” from “the service can accept traffic”. Route only to ready CUs, remove unhealthy CUs from discovery, and make startup failures visible to callers and operators.

## Current state

- `ComputeUnit.executeWorkload()` marks the CU running as soon as `RuntimeInterface.submit()` returns.
- For a JAR, that return means the OS accepted the process launch. It does not mean Spring Boot (or another server) has bound its port.
- DNS currently registers an endpoint on the running event. Thus a just-launched but not-yet-listening application can receive traffic.
- `ComputeUnit.acquireSlot()` currently checks the running state and semaphore capacity. There is no probe policy, monitor, or health state.

## Design principles

- Keep `ComputeUnit` responsible for lifecycle state, not for implementing HTTP probes.
- Use a separate `HealthMonitor` to perform probes and report typed results to the CU lifecycle.
- Treat readiness and liveness as different questions:
  - **Readiness:** should this instance receive new requests now?
  - **Liveness:** is this instance still functioning, or should it be marked unhealthy/restarted?
- DNS is updated only by lifecycle events. It must not probe services itself.
- Probe the endpoint returned by `RuntimeSubmission` directly. Do not resolve the service through DNS to perform its own readiness check.

## Proposed lifecycle

Use explicit states/events rather than overloading `RUNNING`:

```text
AVAILABLE -> RESERVED -> STARTING -> READY -> STOPPING -> RESERVED
                         |             |
                         v             v
                      FAILED       UNHEALTHY
```

- `STARTING`: runtime launch succeeded and an endpoint exists, but readiness has not passed. Do not publish it to DNS.
- `READY`: readiness policy passed. Publish a typed ready event with `ServiceIdentity` and `ServiceEndpoint`; DNS registers it.
- `UNHEALTHY`: readiness/liveness policy failed its configured threshold. Remove it from DNS and deny new request slots.
- `FAILED`: startup timed out, the runtime exited before readiness, or startup otherwise failed. Preserve the error/reason for status output.
- Stop/release/kill must cancel outstanding probes and remove the DNS entry before the endpoint/lease is discarded.

Keep the runtime endpoint private to the CU/health-monitor lifecycle. Do not reintroduce a public `ComputeUnit.getPort()` or mutable port setter.

## Probe policy

Add an immutable `HealthCheckPolicy` configured per CU (and copied into an autoscaling group template). Start with an HTTP GET readiness probe because the gateway serves HTTP:

- Probe path and expected success status range.
- Initial startup timeout and optional initial delay.
- Probe interval and per-probe timeout.
- Consecutive success threshold before becoming READY.
- Consecutive failure threshold before becoming UNHEALTHY.

Use conservative defaults and allow health checks to be disabled only for workloads that explicitly opt out. Keep TCP or custom-command checks as future strategies rather than embedding them in `ComputeUnit`.

## HealthMonitor responsibilities

- Use one managed scheduled executor (or a small bounded pool), not one unbounded thread per CU.
- Start monitoring after runtime submission and stop/cancel monitoring on stop, release, or kill.
- Apply connection and response timeouts to every probe.
- Record last probe time/result and the reason for the last state transition for status/diagnostics.
- Notify the CU lifecycle with a result; the CU changes state and publishes events. The monitor must not directly mutate DNS.
- Observe runtime termination where supported (JAR `Process.onExit`; Docker runtime status/inspect). A dead process is not ready even if the last HTTP probe succeeded.

## DNS and gateway changes

- Replace “process started” as the DNS-registration condition with “ready”.
- Add typed ready/unready or ready/stopped events. DNS registers on READY and removes on UNHEALTHY, STOPPING, FAILED, release, or kill.
- `ComputeUnit.acquireSlot()` should accept requests only in READY. The gateway should return an explicit unavailable response when the CU exists but is not ready.
- Existing direct-CU routing remains pinned to that CU. Load balancer membership (see the separate autoscaling plan) must include only ready CUs.

## Implementation sequence

1. Add explicit starting/ready/unhealthy/failed lifecycle states and typed events.
2. Add and validate `HealthCheckPolicy`.
3. Implement `HealthMonitor` and an injectable probe abstraction for deterministic tests.
4. Store the returned endpoint privately during startup and publish it to DNS only after readiness succeeds.
5. Update gateway slot acquisition and status/list output to expose state and failure reason.
6. Handle process/container exit, startup timeout, cancellation, and cleanup.
7. Integrate the monitor with autoscaling so unhealthy members are removed from routing and can be replaced by policy.

## Verification

- A local test HTTP server that initially fails then succeeds proves the CU stays out of DNS until the success threshold is met.
- A server that becomes unhealthy proves new requests are rejected and the DNS entry is removed after the failure threshold.
- A probe that times out proves the monitor remains bounded and transitions state only according to policy.
- A runtime that exits during startup proves the CU enters FAILED and releases its runtime resources.
- Stop/release/kill tests prove probes are cancelled, DNS entries are removed, and no completion callback can return a terminated CU to READY.
- Gateway tests prove only READY CUs acquire slots.

## Acceptance criteria

- A launched process is not considered traffic-ready solely because `submit()` returned.
- DNS and routing contain only ready endpoints.
- Probe failure and runtime exit lead to an explicit state and observable reason.
- Readiness checks cannot hang gateway request threads or prevent CU cleanup.
- Health policy and monitor behavior can be tested without Docker or external services.

## Relationship to other plans

- The Docker runtime can implement this policy through the same published host endpoint as a JAR runtime.
- The load balancer and autoscaling group plan depends on READY/UNHEALTHY states to select members safely.
- This plan does not define automatic restart policy; restart/replacement behavior belongs to the runtime or autoscaling policy and should be specified separately.
