package io.github.parkkevinsb.bloom;

import java.util.concurrent.Executor;

/**
 * Decorator that dispatches publishes to a delegate bus on a caller-supplied
 * {@link Executor}. Subscriptions are still managed by the delegate.
 *
 * <p>Bloom intentionally does not own thread pools; the lifecycle (including
 * shutdown) of the supplied executor is the caller's responsibility.
 *
 * <pre>{@code
 * EventBus bus = new AsyncEventBus(LocalEventBus.create(), Executors.newCachedThreadPool());
 * }</pre>
 */
public final class AsyncEventBus implements EventBus {

    private final EventBus delegate;
    private final Executor executor;

    public AsyncEventBus(EventBus delegate, Executor executor) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }
        this.delegate = delegate;
        this.executor = executor;
    }

    @Override
    public <E> Subscription subscribe(Class<E> eventType, EventHandler<E> handler) {
        return delegate.subscribe(eventType, handler);
    }

    @Override
    public void publish(Object event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        executor.execute(() -> delegate.publish(event));
    }
}
