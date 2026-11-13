package org.cloudServices.infra.computeUnit;

import org.cloudServices.global.eventbus.EventBus;
import org.cloudServices.infra.runtimes.JarRuntime.JarExecutorRuntime;
import org.cloudServices.infra.runtimes.RuntimeInterface;
import org.cloudServices.infra.runtimes.RuntimeRegistry;
import org.cloudServices.infra.runtimes.Workload;
import org.cloudServices.shared.enums.Events;
import org.cloudServices.shared.interfaces.GatewayServerIntegration;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class ComputeUnit implements GatewayServerIntegration {
    private final UUID id;
    private final LocalDateTime createdAt;
    private final String name;
    private Workload workload;
    private RuntimeInterface runtimeInterface;
    private ComputeUnitState state;
    private String pid;


    public ComputeUnit(Workload workload, String name) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.createdAt = LocalDateTime.now();
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
        EventBus.publish(new EventBus.Event(Events.COMPUTE_UNIT_RESERVED, this));
    }

    public String executeWorkload() {
        if (this.state != ComputeUnitState.RESERVED) throw new IllegalStateException("Compute unit is not reserved");
        this.pid = this.runtimeInterface.submit();
        this.state = ComputeUnitState.RUNNING;
        return this.pid;
    }

    public void terminateWorkload() {
        if (this.state != ComputeUnitState.RUNNING) throw new IllegalStateException("Compute unit is not running");
        this.runtimeInterface.stop();
        this.state = ComputeUnitState.RESERVED;
    }

    public void releaseWorkload() {
        if (this.state != ComputeUnitState.RESERVED && this.state != ComputeUnitState.AVAILABLE) throw new IllegalStateException("Compute unit is not reserved or available");
        this.workload = null;
        this.runtimeInterface = null;
        this.state = ComputeUnitState.AVAILABLE;
        EventBus.publish(new EventBus.Event(Events.COMPUTE_UNIT_RELEASED, this));
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

    public RuntimeInterface getRuntimeInterface() {
        return runtimeInterface;
    }

    public ComputeUnitState getState() {
        return state;
    }

    public String getPid() {
        return pid;
    }

    public String getName() {
        return name;
    }

    public int getPort() {
        if (runtimeInterface instanceof JarExecutorRuntime jarRuntime) {
            return jarRuntime.getPort();
        }
        return 0;
    }

    @Override
    public void executeRunnableTask(Runnable task) {

    }
}
