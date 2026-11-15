package org.cloudServices.global.dns;

import org.cloudServices.global.eventbus.EventBus;
import org.cloudServices.shared.events.ServiceIdentity;
import org.cloudServices.shared.events.ServiceStarted;
import org.cloudServices.shared.runtime.ServiceEndpoint;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Dns {
    private static final Map<String, ServiceEndpoint> dnsRegistry = new ConcurrentHashMap<>();

    public static void init() {
        EventBus.subscribe(event -> {
            switch (event) {
                case EventBus.ServiceStartedEvent startedEvent -> {
                    ServiceStarted started = startedEvent.started();
                    ServiceIdentity service = started.service();
                    dnsRegistry.put(service.id(), started.endpoint());
                    if (service.name() != null && !service.name().isBlank()) {
                        dnsRegistry.put(service.name(), started.endpoint());
                    }
                }
                case EventBus.ServiceStoppedEvent stopped -> remove(stopped.identity());
                case EventBus.ServiceKilledEvent killed -> remove(killed.identity());
                default -> { }
            }
        });
    }

    private static void remove(ServiceIdentity service) {
        dnsRegistry.remove(service.id());
        if (service.name() != null && !service.name().isBlank()) {
            dnsRegistry.remove(service.name());
        }
    }

    public static ServiceEndpoint resolve(String idOrName) {
        return dnsRegistry.get(idOrName);
    }
}
