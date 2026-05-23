package io.github.parkkevinsb.bloom;

/**
 * Static accessor for the process-wide default {@link EventBus} instance.
 *
 * <p>The instance is a {@link LocalEventBus} created lazily on first access.
 * Tests should construct their own {@link LocalEventBus} via
 * {@link LocalEventBus#create()} to remain isolated from other tests sharing
 * the JVM.
 */
public final class GlobalEventBus {

    private static final class Holder {
        static final EventBus INSTANCE = LocalEventBus.create();
    }

    private GlobalEventBus() { }

    /** @return the shared default event bus */
    public static EventBus getInstance() {
        return Holder.INSTANCE;
    }
}
