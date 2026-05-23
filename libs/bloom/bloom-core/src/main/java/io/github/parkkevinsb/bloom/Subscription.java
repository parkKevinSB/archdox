package io.github.parkkevinsb.bloom;

/**
 * Handle representing one active subscription on an {@link EventBus}.
 *
 * <p>{@link #close()} cancels the subscription so future events are no longer
 * delivered to its handler. The call is idempotent and never throws.
 */
public interface Subscription extends AutoCloseable {

    /**
     * Cancels this subscription. Subsequent calls have no effect.
     */
    @Override
    void close();

    /**
     * @return {@code true} while this subscription is still registered
     */
    boolean isActive();
}
