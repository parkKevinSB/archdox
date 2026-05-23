package io.github.parkkevinsb.bloom;

/**
 * Receiver of events of a single type {@code E}.
 *
 * <p>Implementations should return promptly. Long-running work belongs on a
 * dedicated executor (see {@link AsyncEventBus}).
 *
 * @param <E> event type this handler accepts
 */
@FunctionalInterface
public interface EventHandler<E> {

    /**
     * Handles a single event.
     *
     * @param event the event instance
     */
    void handle(E event);
}
