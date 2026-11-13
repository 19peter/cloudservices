package org.cloudServices.global.dns;

import org.cloudServices.global.eventbus.EventBus;
import org.cloudServices.infra.computeUnit.ComputeUnit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Dns {
    //Id -> URL
    private static final Map<String, String> dnsRegistry = new ConcurrentHashMap<>();

    public static void init() {
        EventBus.subscribe(event -> {
            switch (event.event()) {
                case COMPUTE_UNIT_RESERVED -> {
                    if (event.service() instanceof ComputeUnit cu) {
                        dnsRegistry.put(cu.getId().toString(), "127.0.0.1:" + cu.getPort());
                    }
                }

                case COMPUTE_UNIT_KILLED, COMPUTE_UNIT_RELEASED -> {
                    if (event.service() instanceof ComputeUnit cu) {
                        dnsRegistry.remove(cu.getId().toString());
                    }
                }



                default -> {
                    // other events (RESERVED, RUNNING, STOPPED, etc.)
                }
            }
        });
    }

    //Get url by id
    public static String resolve(String id) {
        return dnsRegistry.get(id);
    }
}
