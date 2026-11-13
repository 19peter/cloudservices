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
                        if (cu.getName() != null && !cu.getName().isBlank()) {
                            services.put(cu.getName(), cu);
                        }
                    }
                }

                case COMPUTE_UNIT_KILLED -> {
                    if (event.service() instanceof ComputeUnit cu) {
                        services.remove(cu.getId().toString());
                        if (cu.getName() != null && !cu.getName().isBlank()) {
                            services.remove(cu.getName());
                        }
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
