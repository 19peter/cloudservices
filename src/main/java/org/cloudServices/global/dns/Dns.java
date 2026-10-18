package org.cloudServices.global.dns;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Dns {
    private static final Map<String, String> dnsRegistry = new ConcurrentHashMap<>();

    static {

    }

    public static void init() {

    }

    public String getServiceAddress(String id) {
        return dnsRegistry.get(id);
    }
}
