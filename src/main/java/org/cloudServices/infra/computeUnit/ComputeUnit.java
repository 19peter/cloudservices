package org.cloudServices.infra.computeUnit;

import org.cloudServices.global.eventbus.EventBus;
import org.cloudServices.infra.runtimes.RuntimeInterface;
import org.cloudServices.infra.runtimes.RuntimeRegistry;
import org.cloudServices.infra.runtimes.Workload;
import org.cloudServices.shared.events.ServiceIdentity;
import org.cloudServices.shared.events.ServiceStarted;
import org.cloudServices.shared.enums.Events;
import org.cloudServices.shared.interfaces.GatewayServerIntegration;
import org.cloudServices.shared.runtime.RuntimeSubmission;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Semaphore;

public class ComputeUnit implements GatewayServerIntegration {
    private final UUID id;
    private final LocalDateTime createdAt;
    private final String name;
    private final Semaphore capacity;
    private Workload workload;
    private RuntimeInterface runtimeInterface;
    private ComputeUnitState state;
    private String runtimeInstanceId;


    public ComputeUnit(Workload workload, String name, int capacity) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.createdAt = LocalDateTime.now();
        this.capacity = new Semaphore(capacity);
        this.state = ComputeUnitState.AVAILABLE;
        if (workload != null) {
            assignWorkload(workload);
        }
        EventBus.publish(new EventBus.Event(Events.COMPUTE_UNIT_CREATED, this));
    }

    public void assignWorkload(Workload workload) {
        if (this.workload != null || this.state != ComputeUnitState.AVAILABLE) {
            throw new IllegalStateException("Compute unit is not available for assignment");
        }
        this.workload = Objects.requireNonNull(workload, "Workload cannot be null");
        this.runtimeInterface = RuntimeRegistry.createRuntime(workload);
        this.runtimeInterface.boot();
        this.state = ComputeUnitState.RESERVED;
        EventBus.publish(new EventBus.Event(Events.COMPUTE_UNIT_RESERVED, serviceIdentity()));
    }

    public String executeWorkload() {
        if (this.state != ComputeUnitState.RESERVED) throw new IllegalStateException("Compute unit is not reserved");
        RuntimeSubmission submission = this.runtimeInterface.submit();
        this.runtimeInstanceId = submission.runtimeInstanceId();
        this.state = ComputeUnitState.RUNNING;
        EventBus.publish(new EventBus.Event(
                Events.COMPUTE_UNIT_RUNNING,
                new ServiceStarted(serviceIdentity(), submission.endpoint())));
        return this.runtimeInstanceId;
    }

    public void terminateWorkload() {
        if (this.state != ComputeUnitState.RUNNING) throw new IllegalStateException("Compute unit is not running");
        this.runtimeInterface.stop();
        this.state = ComputeUnitState.RESERVED;
        EventBus.publish(new EventBus.Event(Events.COMPUTE_UNIT_RESERVED, serviceIdentity()));
    }

    public void releaseWorkload() {
        if (this.state != ComputeUnitState.RESERVED && this.state != ComputeUnitState.AVAILABLE) throw new IllegalStateException("Compute unit is not reserved or available");
        if (this.runtimeInterface != null) {
            this.runtimeInterface.stop();
        }
        this.workload = null;
        this.runtimeInterface = null;
        this.state = ComputeUnitState.AVAILABLE;
        EventBus.publish(new EventBus.Event(Events.COMPUTE_UNIT_RELEASED, serviceIdentity()));
    }

    public void killInstance() {
        if (this.state == ComputeUnitState.RUNNING) {
            terminateWorkload();
        }
        if (this.state == ComputeUnitState.RESERVED) {
            releaseWorkload();
        }
        this.state = ComputeUnitState.TERMINATED;
        EventBus.publish(new EventBus.Event(Events.COMPUTE_UNIT_KILLED, this.id));
    }

    public UUID getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Workload getWorkload() {
        return workload;
    }

    public ComputeUnitState getState() {
        return state;
    }

    public String getRuntimeInstanceId() {
        return runtimeInstanceId;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean acquireSlot() {
        if (this.state != ComputeUnitState.RUNNING) {
            return false;
        }
        return capacity.tryAcquire();
    }

    @Override
    public void releaseSlot() {
        capacity.release();
    }

    private ServiceIdentity serviceIdentity() {
        return new ServiceIdentity(id.toString(), name);
    }
}
