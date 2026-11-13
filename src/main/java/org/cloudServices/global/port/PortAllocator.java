package org.cloudServices.global.port;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cloudServices.global.eventbus.EventBus;
import org.cloudServices.shared.enums.Events;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global PortAllocator managing dynamic port assignment for workloads.
 * Verifies local port availability and releases ports upon compute unit termination.
 */
public final class PortAllocator {
    private static final Logger logger = LogManager.getLogger(PortAllocator.class);

    private static final int DEFAULT_START_PORT = 8081;
    private static final int DEFAULT_END_PORT = 8999;

    private static final Set<Integer> allocatedPorts = ConcurrentHashMap.newKeySet();
    private static final Map<String, Integer> unitPortMap = new ConcurrentHashMap<>();

    private PortAllocator() {
        // Prevent instantiation of static utility class
    }

    static {
        init();
    }

    public static void init() {
        EventBus.subscribe(event -> {
            if (event.event() == Events.COMPUTE_UNIT_KILLED) {
                if (event.service() instanceof UUID uuid) {
                    releaseByUnitId(uuid.toString());
                } else if (event.service() instanceof String id) {
                    releaseByUnitId(id);
                }
            }
        });
    }

    /**
     * Allocates the next available port in the default range (8081 - 8999).
     *
     * @return an available port number
     */
    public static synchronized int allocate() {
        return allocate(DEFAULT_START_PORT, DEFAULT_END_PORT);
    }

    /**
     * Allocates an available port and associates it with a unit ID.
     *
     * @param unitId the identifier of the compute unit
     * @return an available port number
     */
    public static synchronized int allocate(String unitId) {
        if (unitId != null && unitPortMap.containsKey(unitId)) {
            return unitPortMap.get(unitId);
        }
        int port = allocate(DEFAULT_START_PORT, DEFAULT_END_PORT);
        if (unitId != null) {
            unitPortMap.put(unitId, port);
        }
        return port;
    }

    /**
     * Allocates an available port within a custom range.
     *
     * @param startPort the lower bound (inclusive)
     * @param endPort   the upper bound (inclusive)
     * @return an available port number
     */
    public static synchronized int allocate(int startPort, int endPort) {
        for (int port = startPort; port <= endPort; port++) {
            if (!allocatedPorts.contains(port) && isPortAvailable(port)) {
                allocatedPorts.add(port);
                logger.info("Allocated port: {}", port);
                return port;
            }
        }
        throw new IllegalStateException("No available ports found in range [" + startPort + " - " + endPort + "]");
    }

    /**
     * Releases an allocated port back to the pool.
     *
     * @param port the port to release
     */
    public static synchronized void release(int port) {
        if (allocatedPorts.remove(port)) {
            unitPortMap.values().removeIf(p -> p == port);
            logger.info("Released port: {}", port);
        }
    }

    /**
     * Releases the port associated with a unit ID.
     *
     * @param unitId the unit ID whose port should be released
     */
    public static synchronized void releaseByUnitId(String unitId) {
        if (unitId == null) return;
        Integer port = unitPortMap.remove(unitId);
        if (port != null) {
            allocatedPorts.remove(port);
            logger.info("Released port {} for unit: {}", port, unitId);
        }
    }

    /**
     * Gets the port allocated for a unit ID, or null if not allocated.
     *
     * @param unitId the unit ID
     * @return the allocated port or null
     */
    public static Integer getPortForUnit(String unitId) {
        return unitPortMap.get(unitId);
    }

    /**
     * Checks if a port is currently available for binding on localhost.
     *
     * @param port the port number to test
     * @return true if available, false if in use
     */
    public static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Returns an unmodifiable set of all currently allocated ports.
     */
    public static Set<Integer> getAllocatedPorts() {
        return Collections.unmodifiableSet(allocatedPorts);
    }
}
