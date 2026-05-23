package io.github.parkkevinsb.bloom;

import java.util.ArrayList;
import java.util.List;

/**
 * Convenience base for objects that own multiple subscriptions and want to
 * release them together.
 *
 * <p>Subclasses register subscriptions during setup via
 * {@link #on(EventBus, Class, EventHandler)}. {@link #close()} cancels every
 * accumulated subscription and clears the internal list.
 *
 * <p>This base does <em>not</em> mean a single handler may serve multiple event
 * types; each call to {@code on} still binds one handler to one type.
 */
public abstract class AbstractEventSubscriber implements AutoCloseable {

    private final List<Subscription> subscriptions = new ArrayList<>();

    /**
     * Registers a handler on {@code bus} for the given event type, tracks the
     * resulting subscription for later release, and returns the handle.
     */
    protected final synchronized <E> Subscription on(
            EventBus bus,
            Class<E> eventType,
            EventHandler<E> handler) {
        if (bus == null) {
            throw new IllegalArgumentException("bus must not be null");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("eventType must not be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }
        Subscription subscription = bus.subscribe(eventType, handler);
        subscriptions.add(subscription);
        return subscription;
    }

    /** Cancels every tracked subscription. */
    @Override
    public void close() {
        Subscription[] copy;
        synchronized (this) {
            copy = subscriptions.toArray(new Subscription[0]);
            subscriptions.clear();
        }
        for (Subscription subscription : copy) {
            subscription.close();
        }
    }
}
