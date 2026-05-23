package io.github.parkkevinsb.bloom;

import io.github.parkkevinsb.bloom.internal.DefaultSubscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory, synchronous event bus.
 *
 * <p>Each instance owns an isolated handler registry. Handlers are invoked on
 * the publishing thread. A failing handler does not propagate its exception to
 * other handlers or to the publisher; failures are routed to the configured
 * {@link ErrorHandler} (default: {@link ErrorHandler#IGNORE}).
 *
 * <p>Type matching is exact: a subscription on {@code Foo.class} only receives
 * events whose runtime class is exactly {@code Foo}.
 *
 * <p>This class is thread-safe.
 */
public final class LocalEventBus implements EventBus {

    private final Map<Class<?>, CopyOnWriteArrayList<HandlerEntry<?>>> handlers
            = new ConcurrentHashMap<>();

    private volatile ErrorHandler errorHandler = ErrorHandler.IGNORE;

    private LocalEventBus() { }

    /** @return a new, empty event bus */
    public static LocalEventBus create() {
        return new LocalEventBus();
    }

    /**
     * Replaces the strategy invoked when a handler throws. Pass
     * {@link ErrorHandler#IGNORE} to restore the silent default.
     *
     * @param handler the new error handler; must not be null
     */
    public void onListenerError(ErrorHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }
        this.errorHandler = handler;
    }

    @Override
    public <E> Subscription subscribe(Class<E> eventType, EventHandler<E> handler) {
        if (eventType == null) {
            throw new IllegalArgumentException("eventType must not be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }

        CopyOnWriteArrayList<HandlerEntry<?>> list =
                handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());

        HandlerEntry<E> entry = new HandlerEntry<>(eventType, handler);
        list.add(entry);

        return new DefaultSubscription(() -> {
            CopyOnWriteArrayList<HandlerEntry<?>> existing = handlers.get(eventType);
            if (existing != null) {
                existing.remove(entry);
            }
        });
    }

    @Override
    public void publish(Object event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }

        CopyOnWriteArrayList<HandlerEntry<?>> list = handlers.get(event.getClass());
        if (list == null || list.isEmpty()) {
            return;
        }

        // Explicit snapshot: handlers added during this dispatch do not see this event.
        List<HandlerEntry<?>> snapshot = new ArrayList<>(list);
        for (HandlerEntry<?> entry : snapshot) {
            dispatch(event, entry);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void dispatch(Object event, HandlerEntry entry) {
        try {
            entry.handler.handle(event);
        } catch (Throwable t) {
            ErrorHandler eh = errorHandler;
            if (eh != null) {
                try {
                    eh.onError(event, entry.handler, t);
                } catch (Throwable ignored) {
                    // secondary failure inside the ErrorHandler is intentionally swallowed
                }
            }
        }
    }

    private static final class HandlerEntry<E> {
        final Class<E> eventType;
        final EventHandler<E> handler;

        HandlerEntry(Class<E> eventType, EventHandler<E> handler) {
            this.eventType = eventType;
            this.handler = handler;
        }
    }
}
