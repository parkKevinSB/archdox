package io.github.parkkevinsb.bloom.internal;

import io.github.parkkevinsb.bloom.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default {@link Subscription} that runs a removal callback on the first
 * {@link #close()} and is a no-op on subsequent calls.
 *
 * <p>This class is part of the internal API. It is public only because it
 * crosses package boundaries within {@code bloom-core}; external callers must
 * not depend on it.
 */
public final class DefaultSubscription implements Subscription {

    private final Runnable onClose;
    private final AtomicBoolean active = new AtomicBoolean(true);

    public DefaultSubscription(Runnable onClose) {
        if (onClose == null) {
            throw new IllegalArgumentException("onClose must not be null");
        }
        this.onClose = onClose;
    }

    @Override
    public void close() {
        if (active.compareAndSet(true, false)) {
            try {
                onClose.run();
            } catch (Throwable ignored) {
                // Subscription.close() is specified to never throw.
            }
        }
    }

    @Override
    public boolean isActive() {
        return active.get();
    }
}
