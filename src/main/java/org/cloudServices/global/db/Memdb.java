package org.cloudServices.global.db;

import org.cloudServices.global.eventbus.EventBus;
import org.cloudServices.infra.computeUnit.ComputeUnit;
import org.cloudServices.shared.events.ServiceIdentity;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class Memdb {
    private static final Map<String, ComputeUnit> services = new ConcurrentHashMap<>();

    public static void init() {
        EventBus.subscribe(event -> {
            switch (event) {
                case EventBus.ServiceKilledEvent killed -> {
                    ServiceIdentity identity = killed.identity();
                    services.remove(identity.id());
                    if (identity.name() != null && !identity.name().isBlank()) {
                        services.remove(identity.name());
                    }
                }
                default -> { }
            }
        });
    }

    public static void register(ComputeUnit computeUnit) {
        Objects.requireNonNull(computeUnit, "computeUnit cannot be null");
        services.put(computeUnit.getId().toString(), computeUnit);
        if (computeUnit.getName() != null && !computeUnit.getName().isBlank()) {
            services.put(computeUnit.getName(), computeUnit);
        }
    }

    public static ComputeUnit getInstance(String idOrName) {
        ComputeUnit instance = services.get(idOrName);
        if (instance != null) {
            return instance;
        }
        for (ComputeUnit candidate : services.values()) {
            if (candidate.getName() != null && candidate.getName().equalsIgnoreCase(idOrName)) {
                return candidate;
            }
        }
        return null;
    }

    public static Map<String, ComputeUnit> getAll() {
        return Collections.unmodifiableMap(services);
    }
}
