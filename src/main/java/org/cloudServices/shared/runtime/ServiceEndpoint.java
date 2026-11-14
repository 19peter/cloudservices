package org.cloudServices.shared.runtime;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

public record ServiceEndpoint(URI uri) {
    public ServiceEndpoint {
        Objects.requireNonNull(uri, "uri cannot be null");
        if (!uri.isAbsolute() || uri.getHost() == null || uri.getPort() < 1 || uri.getPort() > 65535) {
            throw new IllegalArgumentException("Endpoint URI must include a scheme, host, and port");
        }
    }

    public static ServiceEndpoint http(String host, int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        try {
            return new ServiceEndpoint(new URI("http", null, host, port, null, null, null));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid endpoint host: " + host, e);
        }
    }
}
