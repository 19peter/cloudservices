package org.cloudServices.infra.runtimes.JSRuntime;

import org.cloudServices.infra.runtimes.Workload;

public record JSFileWorkload(String filePath) implements Workload {
    
}
