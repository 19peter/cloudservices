package org.cloudServices;

import org.cloudServices.servers.DaemonServer;
import org.cloudServices.global.db.HealthRegistry;
import org.cloudServices.global.db.Memdb;
import org.cloudServices.global.dns.Dns;
import org.cloudServices.servers.GatewayServer;

public class Main {
    public static void main(String[] args) {
        Memdb.init();
        Dns.init();
        HealthRegistry.init();
        GatewayServer.start();
        DaemonServer.start();
    }
}