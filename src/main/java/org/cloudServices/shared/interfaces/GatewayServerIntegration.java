package org.cloudServices.shared.interfaces;

public interface GatewayServerIntegration {
    boolean acquireSlot();

    void releaseSlot();
}
