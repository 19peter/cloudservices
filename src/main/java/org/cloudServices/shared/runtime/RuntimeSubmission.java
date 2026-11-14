package org.cloudServices.shared.runtime;

import java.util.Objects;

public record RuntimeSubmission(String processId, ServiceEndpoint endpoint) {
    public RuntimeSubmission {
        if (processId == null || processId.isBlank()) {
            throw new IllegalArgumentException("processId cannot be null or blank");
        }
        Objects.requireNonNull(endpoint, "endpoint cannot be null");
    }
}
