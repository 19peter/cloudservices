package org.cloudServices.infra.runtimes;

import org.cloudServices.shared.runtime.RuntimeSubmission;

public interface RuntimeInterface {
    void boot();
    RuntimeSubmission submit();
    void stop();
}
