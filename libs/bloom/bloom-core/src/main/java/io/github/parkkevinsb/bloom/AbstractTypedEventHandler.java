package io.github.parkkevinsb.bloom;

/**
 * Convenience base class for handlers expressed as a class rather than a lambda.
 *
 * <p>An instance is bound to one event type and holds its own {@link Subscription},
 * so the same object can subscribe and later release itself via {@link #close()}.
 * This preserves Bloom's rule that one handler handles exactly one event type.
 *
 * <pre>{@code
 * public final class OrderPlacedHandler extends AbstractTypedEventHandler<OrderPlaced> {
 *     public OrderPlacedHandler() { super(OrderPlaced.class); }
 *     @Override protected void onEvent(OrderPlaced event) { ... }
 * }
 *
 * OrderPlacedHandler h = new OrderPlacedHandler();
 * h.subscribeTo(bus);
 * ...
 * h.close();
 * }</pre>
 *
 * @param <E> event type this handler accepts
 */
public abstract class AbstractTypedEventHandler<E> implements EventHandler<E>, AutoCloseable {

    private final Class<E> eventType;
    private Subscription subscription;

    protected AbstractTypedEventHandler(Class<E> eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("eventType must not be null");
        }
        this.eventType = eventType;
    }

    /** @return the event type this handler is bound to */
    public final Class<E> eventType() {
        return eventType;
    }

    /**
     * Subscribes this handler to {@code bus} if not already active. Repeated
     * calls return the existing active subscription.
     *
     * @return the active {@link Subscription}
     */
    public final synchronized Subscription subscribeTo(EventBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("bus must not be null");
        }
        if (subscription == null || !subscription.isActive()) {
            subscription = bus.subscribe(eventType, this);
        }
        return subscription;
    }

    @Override
    public final void handle(E event) {
        onEvent(event);
    }

    /** Subclass logic for a single event. */
    protected abstract void onEvent(E event);

    /** Cancels the held subscription, if any. Idempotent. */
    @Override
    public final void close() {
        Subscription toClose;
        synchronized (this) {
            toClose = subscription;
            subscription = null;
        }
        if (toClose != null) {
            toClose.close();
        }
    }
}
