package org.cloudServices.global.db;

import org.cloudServices.global.eventbus.EventBus;
import org.cloudServices.infra.computeUnit.ComputeUnit;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Memdb {
    private static final Map<String, ComputeUnit> services = new ConcurrentHashMap<>();

    public static void init() {
        EventBus.subscribe(event -> {
            switch (event.event()) {
                case COMPUTE_UNIT_CREATED -> {
                    if (event.service() instanceof ComputeUnit cu) {
                        services.put(cu.getId().toString(), cu);
                    }
                }

                case COMPUTE_UNIT_KILLED -> {
                    if (event.service() instanceof ComputeUnit cu) {
                        services.remove(cu.getId().toString());
                    } else if (event.service() instanceof UUID uuid) {
                        services.remove(uuid.toString());
                    } else if (event.service() instanceof String id) {
                        services.remove(id);
                    }
                }
                default -> {
                    // other events (RESERVED, RUNNING, STOPPED, etc.)
                }
            }
        });
    }

    public static ComputeUnit getInstance(String id) {
        return services.get(id);
    }

    public static Map<String, ComputeUnit> getAll() {
        return Collections.unmodifiableMap(services);
    }
}
