package io.github.parkkevinsb.bloom;

/**
 * Service provider interface for an event bus.
 *
 * <p>An {@code EventBus} dispatches events from publishers to handlers that
 * have subscribed to a specific event type. Matching is exact: a subscription
 * on {@code Foo.class} only receives events whose runtime class is exactly
 * {@code Foo}.
 *
 * <p>Implementations are expected to be thread-safe.
 */
public interface EventBus {

    /**
     * Subscribes a handler to a specific event type.
     *
     * @param eventType runtime type of events the handler accepts
     * @param handler   handler invoked when matching events are published
     * @param <E>       event type
     * @return a {@link Subscription} representing this registration
     * @throws IllegalArgumentException if {@code eventType} or {@code handler} is null
     */
    <E> Subscription subscribe(Class<E> eventType, EventHandler<E> handler);

    /**
     * Publishes an event to all handlers registered for its exact runtime type.
     *
     * @param event the event to dispatch; must not be null
     * @throws IllegalArgumentException if {@code event} is null
     */
    void publish(Object event);
}
