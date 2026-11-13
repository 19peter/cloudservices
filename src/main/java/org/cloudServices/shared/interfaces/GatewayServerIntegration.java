package org.cloudServices.shared.interfaces;

import com.sun.net.httpserver.HttpExchange;

public interface GatewayServerIntegration {
    void dispatch(HttpExchange exchange, String subpath);
}
