package org.cloudServices.global.dns;

import org.cloudServices.global.eventbus.EventBus;
import org.cloudServices.infra.computeUnit.ComputeUnit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Dns {
    private static final Map<String, String> dnsRegistry = new ConcurrentHashMap<>();

    public static void init() {
        EventBus.subscribe(event -> {
            switch (event.event()) {
                case COMPUTE_UNIT_RESERVED -> {
                    if (event.service() instanceof ComputeUnit cu) {
                        String targetUrl = "http://127.0.0.1:" + cu.getPort();
                        dnsRegistry.put(cu.getId().toString(), targetUrl);
                        if (cu.getName() != null && !cu.getName().isBlank()) {
                            dnsRegistry.put(cu.getName(), targetUrl);
                        }
                    }
                }

                case COMPUTE_UNIT_KILLED, COMPUTE_UNIT_RELEASED -> {
                    if (event.service() instanceof ComputeUnit cu) {
                        dnsRegistry.remove(cu.getId().toString());
                        if (cu.getName() != null && !cu.getName().isBlank()) {
                            dnsRegistry.remove(cu.getName());
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

    public static String resolve(String idOrName) {
        return dnsRegistry.get(idOrName);
    }
}
