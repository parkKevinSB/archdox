package com.archdox.cloud.global.flow;

import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.worker.DuplicatePolicy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class FlowerFlowAsyncCompletionService {
    private static final Duration POLL_INTERVAL = Duration.ofMillis(25);

    private final Engine engine;

    public FlowerFlowAsyncCompletionService(Engine engine) {
        this.engine = engine;
    }

    public void submit(String workerName, Flow flow) {
        engine.worker(workerName).submit(flow, DuplicatePolicy.REJECT);
    }

    public CompletableFuture<Boolean> submitAndTrackTerminal(String workerName, Flow flow, Duration timeout) {
        try {
            submit(workerName, flow);
        } catch (RuntimeException ex) {
            return CompletableFuture.failedFuture(ex);
        }
        return awaitTerminal(flow, timeout);
    }

    public CompletableFuture<Boolean> awaitTerminal(Flow flow, Duration timeout) {
        var result = new CompletableFuture<Boolean>();
        poll(flow, result, System.nanoTime() + timeout.toNanos());
        return result;
    }

    private void poll(Flow flow, CompletableFuture<Boolean> result, long deadlineNanos) {
        if (result.isDone()) {
            return;
        }
        if (flow.state().isTerminal()) {
            result.complete(true);
            return;
        }
        if (System.nanoTime() >= deadlineNanos) {
            flow.cancel();
            result.complete(false);
            return;
        }
        try {
            CompletableFuture.runAsync(
                    () -> poll(flow, result, deadlineNanos),
                    CompletableFuture.delayedExecutor(POLL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS))
                    .exceptionally(error -> {
                        result.completeExceptionally(error);
                        return null;
                    });
        } catch (RejectedExecutionException ex) {
            result.completeExceptionally(ex);
        }
    }
}
