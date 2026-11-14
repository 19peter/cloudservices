package org.cloudServices.shared.events;

public record ServiceIdentity(String id, String name) {
    public ServiceIdentity {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id cannot be null or blank");
        }
    }
}
