package org.cloudServices;

import org.cloudServices.cmd.CommandDispatcher;
import org.cloudServices.cmd.DaemonServer;
import org.cloudServices.global.db.Memdb;
import org.cloudServices.global.dns.Dns;

public class Main {
    public static void main(String[] args) {
        Memdb.init();
        Dns.init();
        CommandDispatcher.init();
        DaemonServer.start();
    }
}