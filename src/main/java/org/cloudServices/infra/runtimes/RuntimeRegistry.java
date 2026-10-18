package org.cloudServices.infra.runtimes;

import org.cloudServices.infra.runtimes.JSRuntime.JSExecutorRuntime;
import org.cloudServices.infra.runtimes.JSRuntime.JSFileWorkload;
import org.cloudServices.infra.runtimes.JarRuntime.JarExecutorRuntime;
import org.cloudServices.infra.runtimes.JarRuntime.JarWorkload;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Static registry and factory for resolving and instantiating {@link RuntimeInterface}
 * instances dynamically based on a given {@link Workload} type.
 */
public class RuntimeRegistry {

    private static final Map<Class<? extends Workload>, Function<? extends Workload, RuntimeInterface>> factories = new ConcurrentHashMap<>();

    static {
        registerDefaults();
    }

    private RuntimeRegistry() {
        // Prevent direct instantiation
    }

    /**
     * Registers default built-in runtime implementations.
     */
    private static void registerDefaults() {
        register(JarWorkload.class, JarExecutorRuntime::new);
        register(JSFileWorkload.class, JSExecutorRuntime::new);
    }

    /**
     * Registers a factory function for a specific Workload type.
     *
     * @param workloadType the class type of the workload
     * @param factory      the factory function that produces a RuntimeInterface
     * @param <T>          the concrete workload type
     */
    @SuppressWarnings("unchecked")
    public static <T extends Workload> void register(Class<T> workloadType, Function<T, RuntimeInterface> factory) {
        Objects.requireNonNull(workloadType, "workloadType must not be null");
        Objects.requireNonNull(factory, "factory must not be null");
        factories.put(workloadType, (Function<? extends Workload, RuntimeInterface>) factory);
    }

    /**
     * Registers a supplier factory for a specific Workload type when workload instance data is not needed.
     *
     * @param workloadType the class type of the workload
     * @param supplier     the runtime supplier
     * @param <T>          the concrete workload type
     */
    public static <T extends Workload> void register(Class<T> workloadType, Supplier<RuntimeInterface> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        register(workloadType, workload -> supplier.get());
    }

    /**
     * Creates and returns a new {@link RuntimeInterface} instance suitable for the given {@link Workload}.
     *
     * @param workload the workload instance to resolve a runtime for
     * @return a new RuntimeInterface instance
     * @throws IllegalArgumentException if workload is null or if no runtime is registered for the workload type
     */
    @SuppressWarnings("unchecked")
    public static RuntimeInterface createRuntime(Workload workload) {
        if (workload == null) {
            throw new IllegalArgumentException("Workload cannot be null");
        }

        // 1. Direct class match
        Function<Workload, RuntimeInterface> factory =
                (Function<Workload, RuntimeInterface>) factories.get(workload.getClass());

        // 2. Polymorphic match (subtypes/implementations)
        if (factory == null) {
            for (Map.Entry<Class<? extends Workload>, Function<? extends Workload, RuntimeInterface>> entry : factories.entrySet()) {
                if (entry.getKey().isAssignableFrom(workload.getClass())) {
                    factory = (Function<Workload, RuntimeInterface>) entry.getValue();
                    break;
                }
            }
        }

        if (factory == null) {
            throw new IllegalArgumentException("No runtime interface registered for workload type: "
                    + workload.getClass().getName());
        }

        return factory.apply(workload);
    }

    /**
     * Alias for {@link #createRuntime(Workload)}.
     */
    public static RuntimeInterface create(Workload workload) {
        return createRuntime(workload);
    }

    /**
     * Checks if a runtime factory is registered for the specified workload type.
     *
     * @param workloadType the workload class type
     * @return true if a matching factory is found, false otherwise
     */
    public static boolean supports(Class<? extends Workload> workloadType) {
        if (workloadType == null) {
            return false;
        }
        if (factories.containsKey(workloadType)) {
            return true;
        }
        return factories.keySet().stream().anyMatch(type -> type.isAssignableFrom(workloadType));
    }
}
