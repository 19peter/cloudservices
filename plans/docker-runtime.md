# Docker Runtime Plan

## Goal

Run a configured Docker image as a `ComputeUnit` workload and route HTTP traffic to its container through the existing gateway and DNS flow.

## Current state

- [`DockerWorkload`](../src/main/java/org/cloudServices/infra/runtimes/DockerRuntime/DockerWorkload.java) currently contains only an image and optional container name.
- [`DockerExecutorRuntime`](../src/main/java/org/cloudServices/infra/runtimes/DockerRuntime/DockerExecutorRuntime.java) is a placeholder and is not registered in [`RuntimeRegistry`](../src/main/java/org/cloudServices/infra/runtimes/RuntimeRegistry.java).
- The shared runtime result already carries a runtime ID and a typed endpoint. A container ID is not a host process PID, so the public result and status output should use runtime-neutral naming.
- DNS registration is runtime-independent: it consumes the endpoint from the service-start lifecycle event.

## Recommended initial decisions

- Use the installed Docker CLI through `ProcessBuilder`; do not add a Docker SDK dependency for the first implementation.
- Require the image to exist locally initially. Pulling images can be added later as an explicit workload policy rather than an implicit side effect.
- Support one HTTP port per container at first. Make the container's listening port explicit in `DockerWorkload`.
- Publish the host port on `127.0.0.1` only. The gateway is the public ingress and should be the path to the container.
- Keep Docker-specific logic in `DockerExecutorRuntime`; DNS and the gateway should consume the same `ServiceEndpoint` as for JAR workloads.

## Proposed design

### 1. Make runtime identifiers implementation-neutral

- Rename `RuntimeSubmission.processId` to `runtimeInstanceId` (or an equivalent name).
- Update `ComputeUnit` and CLI status/list output to say “Runtime ID”, not “PID”.
- A JAR runtime can return its process ID as the runtime ID; the Docker runtime returns the Docker container ID.
- Keep the endpoint as a separate typed value. Do not put Docker metadata or ports on `ComputeUnit`.

### 2. Define the Docker workload contract

Extend `DockerWorkload` to describe the minimum information required to run and route to a container:

- Image name and optional tag.
- Required container HTTP port.
- Optional requested container name (make it unique per CU or reject collisions).
- Optional environment variables and command/arguments, if those are needed by current workloads.

Validate required values before attempting to start a container. Do not support arbitrary host-volume mounts in the first version; they broaden the security and portability surface.

### 3. Implement container lifecycle

- `boot()` validates the workload and prepares runtime configuration without starting a container or allocating a host port.
- `submit()`:
  1. Allocates a `PortLease`.
  2. Runs Docker in detached mode, publishing `127.0.0.1:<leased-port>:<container-port>`.
  3. Captures and validates the returned container ID.
  4. Returns a `RuntimeSubmission` containing that ID and `http://127.0.0.1:<leased-port>`.
- Use a `ProcessBuilder` argument list, never a shell-concatenated command. Capture stdout/stderr, enforce a command timeout, and report Docker errors.
- If startup fails after creating a container, remove that container when its ID is known and close the port lease.
- `stop()` stops the container by its recorded ID. Close the lease only after the container is confirmed stopped/removed; surface failures rather than reporting a successful stop while the container may still own the port.
- Ensure repeated `stop()` calls are safe.

### 4. Register and expose the workload

- Register `DockerWorkload.class` with `RuntimeRegistry`.
- Add Docker workload parsing/creation to `ComputeUnitController`.
- Use the existing `ComputeUnit` lifecycle and typed service-start event. DNS should not need to know whether the endpoint belongs to a JAR or a container.
- Keep the existing direct-CU gateway route working.

## Implementation sequence

1. Rename runtime submission identifiers and update status output.
2. Expand and validate `DockerWorkload`.
3. Implement a small Docker CLI execution helper or equivalent private methods for command execution, output capture, timeouts, and exit-code handling.
4. Implement `boot`, `submit`, and `stop` with lease cleanup on every failure path.
5. Register the runtime and add controller parsing.
6. Add fake-CLI unit tests, then optional Docker-installed integration tests.

## Verification

- Unit tests cover invalid image/port configuration, Docker command construction, CLI failure output, timeout behavior, returned container ID, endpoint construction, and lease cleanup.
- Integration test with Docker available:
  - Start a small HTTP image.
  - Confirm the runtime returns a container ID and endpoint.
  - Resolve the CU ID through DNS and send a request through the gateway.
  - Stop the CU and confirm the container is removed and the DNS record is gone.
- Docker-dependent tests must be opt-in or clearly skipped when Docker is unavailable.

## Acceptance criteria

- Docker workloads can be created through the normal CU workflow.
- A successfully started container is reachable through the gateway at the CU's DNS identity.
- The runtime retains the container ID, not a misleading host PID.
- Failed starts and stops report errors and do not silently leak a port lease or container.
- Existing JAR workload routing remains unchanged.

## Follow-up constraints

- A port lease is only an in-process allocation; the host port can still be claimed externally between the availability probe and Docker's bind. Docker's non-zero exit must release the lease and be surfaced clearly.
- Container readiness is handled by the separate health/readiness plan. Starting the Docker CLI successfully does not prove the application is ready.
