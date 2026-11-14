package org.cloudServices.infra.runtimes.DockerRuntime;

import org.cloudServices.infra.runtimes.Workload;

public record DockerWorkload(
        String dockerImage,
        String containerName) implements Workload {
}
