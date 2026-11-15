package org.cloudServices.global.eventbus;

import org.cloudServices.shared.enums.HealthStatus;
import org.cloudServices.shared.events.ServiceIdentity;
import org.cloudServices.shared.events.ServiceStarted;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class EventBus {
    public interface EventBusEvent { }

    public record ServiceStartedEvent(ServiceStarted started) implements EventBusEvent {
        public ServiceStartedEvent {
            Objects.requireNonNull(started, "started cannot be null");
        }
    }

    public record ServiceStoppedEvent(ServiceIdentity identity) implements EventBusEvent {
        public ServiceStoppedEvent {
            Objects.requireNonNull(identity, "identity cannot be null");
        }
    }

    public record ServiceKilledEvent(ServiceIdentity identity) implements EventBusEvent {
        public ServiceKilledEvent {
            Objects.requireNonNull(identity, "identity cannot be null");
        }
    }

    public record ServiceHealthChangedEvent(ServiceIdentity identity, HealthStatus status)
            implements EventBusEvent {
        public ServiceHealthChangedEvent {
            Objects.requireNonNull(identity, "identity cannot be null");
            Objects.requireNonNull(status, "status cannot be null");
        }
    }

    private static final List<Consumer<EventBusEvent>> listeners = new CopyOnWriteArrayList<>();

    private EventBus() {
    }

    public static void subscribe(Consumer<EventBusEvent> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener cannot be null"));
    }

    public static void publish(EventBusEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        for (Consumer<EventBusEvent> listener : listeners) {
            listener.accept(event);
        }
    }
}
