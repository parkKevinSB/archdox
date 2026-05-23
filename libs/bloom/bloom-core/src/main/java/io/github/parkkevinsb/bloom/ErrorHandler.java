package io.github.parkkevinsb.bloom;

/**
 * Strategy invoked when a handler throws while processing an event.
 *
 * <p>An error handler isolates failures so other handlers registered for the
 * same event continue to run. The default policy is {@link #IGNORE}.
 */
@FunctionalInterface
public interface ErrorHandler {

    /**
     * Called once per failed handler invocation.
     *
     * @param event   the event being delivered
     * @param handler the handler that threw
     * @param t       the thrown exception
     */
    void onError(Object event, EventHandler<?> handler, Throwable t);

    /** Drops every error silently. */
    ErrorHandler IGNORE = (e, h, t) -> { };
}
