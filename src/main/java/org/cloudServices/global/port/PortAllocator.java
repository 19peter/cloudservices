package org.cloudServices.global.port;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Allocates local port leases for runtimes.
 */
public final class PortAllocator {
    private static final Logger logger = LogManager.getLogger(PortAllocator.class);

    private static final int DEFAULT_START_PORT = 8081;
    private static final int DEFAULT_END_PORT = 8999;

    private static final Set<Integer> allocatedPorts = new HashSet<>();

    private PortAllocator() {
        // Prevent instantiation of static utility class
    }

    /**
     * Allocates the next available port in the default range (8081 - 8999).
     *
     * @return a lease for an available port
     */
    public static synchronized PortLease allocate() {
        return allocate(DEFAULT_START_PORT, DEFAULT_END_PORT);
    }

    /**
     * Allocates an available port within a custom range.
     */
    public static synchronized PortLease allocate(int startPort, int endPort) {
        if (startPort < 1 || endPort > 65535 || startPort > endPort) {
            throw new IllegalArgumentException("Invalid port range [" + startPort + " - " + endPort + "]");
        }
        for (int port = startPort; port <= endPort; port++) {
            if (!allocatedPorts.contains(port) && isPortAvailable(port)) {
                allocatedPorts.add(port);
                logger.info("Allocated port: {}", port);
                return new PortLease(port);
            }
        }
        throw new IllegalStateException("No available ports found in range [" + startPort + " - " + endPort + "]");
    }

    private static synchronized void release(int port) {
        if (allocatedPorts.remove(port)) {
            logger.info("Released port: {}", port);
        }
    }

    /**
     * Checks if a port is currently available for binding on localhost.
     *
     * @param port the port number to test
     * @return true if available, false if in use
     */
    public static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static final class PortLease implements AutoCloseable {
        private final int port;
        private final AtomicBoolean closed = new AtomicBoolean();

        private PortLease(int port) {
            this.port = port;
        }

        public int port() {
            return port;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release(port);
            }
        }
    }
}
