package org.cloudServices.infra.runtimes.DockerRuntime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cloudServices.infra.runtimes.RuntimeInterface;
import org.cloudServices.shared.runtime.RuntimeSubmission;

public class DockerExecutorRuntime implements RuntimeInterface {
    private static final Logger logger = LogManager.getLogger(DockerExecutorRuntime.class);
    private final DockerWorkload workload;
    private int allocatedPort;
    private boolean booted;
    private Process process;
    private ProcessBuilder processBuilder;

    public DockerExecutorRuntime(DockerWorkload workload) {
        this.workload = workload;
    }

    @Override
    public void boot() {

    }

    @Override
    public RuntimeSubmission submit() {
        throw new UnsupportedOperationException("Docker runtime submission is not implemented");
    }

    @Override
    public void stop() {

    }
}
