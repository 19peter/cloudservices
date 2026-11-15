package org.cloudServices.global.db;

import org.cloudServices.global.eventbus.EventBus;
import org.cloudServices.shared.enums.HealthStatus;
import org.cloudServices.shared.events.ServiceIdentity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HealthRegistry {
    private static final Map<ServiceIdentity, HealthStatus> services = new ConcurrentHashMap<>();

    public static void init() {
        EventBus.subscribe(event -> {
            switch (event) {
                case EventBus.ServiceHealthChangedEvent changed ->
                        services.put(changed.identity(), changed.status());
                case EventBus.ServiceStoppedEvent stopped -> services.remove(stopped.identity());
                case EventBus.ServiceKilledEvent killed -> services.remove(killed.identity());
                default -> { }
            }
        });
    }

    public static HealthStatus getStatus(ServiceIdentity identity) {
        return services.get(identity);
    }
}
