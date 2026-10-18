package org.cloudServices.infra.runtimes.JarRuntime;

import org.cloudServices.infra.runtimes.Workload;

import java.util.List;
import java.util.Map;

public record JarWorkload(
    String jarPath,
    List<String> args,
    List<String> jvmOptions,
    Map<String, String> environment) implements Workload {
}
