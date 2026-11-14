package org.cloudServices.global.dns;

import org.cloudServices.global.eventbus.EventBus;
import org.cloudServices.shared.events.ServiceIdentity;
import org.cloudServices.shared.events.ServiceStarted;
import org.cloudServices.shared.runtime.ServiceEndpoint;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Dns {
    private static final Map<String, ServiceEndpoint> dnsRegistry = new ConcurrentHashMap<>();

    public static void init() {
        EventBus.subscribe(event -> {
            switch (event.event()) {
                case COMPUTE_UNIT_RUNNING -> {
                    if (event.service() instanceof ServiceStarted started) {
                        ServiceIdentity service = started.service();
                        dnsRegistry.put(service.id(), started.endpoint());
                        if (service.name() != null && !service.name().isBlank()) {
                            dnsRegistry.put(service.name(), started.endpoint());
                        }
                    }
                }

                case COMPUTE_UNIT_RESERVED, COMPUTE_UNIT_KILLED, COMPUTE_UNIT_RELEASED -> {
                    if (event.service() instanceof ServiceIdentity service) {
                        dnsRegistry.remove(service.id());
                        if (service.name() != null && !service.name().isBlank()) {
                            dnsRegistry.remove(service.name());
                        }
                    } else if (event.service() instanceof UUID uuid) {
                        dnsRegistry.remove(uuid.toString());
                    } else if (event.service() instanceof String id) {
                        dnsRegistry.remove(id);
                    }
                }

                default -> {
                    // other events (RESERVED, RUNNING, STOPPED, etc.)
                }
            }
        });
    }

    public static ServiceEndpoint resolve(String idOrName) {
        return dnsRegistry.get(idOrName);
    }
}
