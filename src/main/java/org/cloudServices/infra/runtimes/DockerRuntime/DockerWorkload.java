package org.cloudServices.infra.runtimes.DockerRuntime;

import org.cloudServices.infra.runtimes.Workload;

import java.util.HashMap;

public record DockerWorkload(
        String dockerImage,
        String containerName,
        String tag,
        Integer port,
        HashMap<String, String> envVars) implements Workload {
}
