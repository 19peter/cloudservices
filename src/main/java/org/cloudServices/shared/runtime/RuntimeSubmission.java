package org.cloudServices.shared.runtime;

import java.util.Objects;

public record RuntimeSubmission(String runtimeInstanceId, ServiceEndpoint endpoint) {
    public RuntimeSubmission {
        if (runtimeInstanceId == null || runtimeInstanceId.isBlank()) {
            throw new IllegalArgumentException("processId cannot be null or blank");
        }
        Objects.requireNonNull(endpoint, "endpoint cannot be null");
    }
}
