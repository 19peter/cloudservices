package org.cloudServices.shared.events;

import org.cloudServices.shared.runtime.ServiceEndpoint;

import java.util.Objects;

public record ServiceStarted(ServiceIdentity service, ServiceEndpoint endpoint) {
    public ServiceStarted {
        Objects.requireNonNull(service, "service cannot be null");
        Objects.requireNonNull(endpoint, "endpoint cannot be null");
    }
}
