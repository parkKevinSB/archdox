package com.archdox.cloud.photo.flow.step;

import com.archdox.cloud.photo.application.PhotoDerivativeProperties;
import com.archdox.cloud.photo.event.PhotoDerivativeGenerationFailed;
import com.archdox.cloud.photo.event.PhotoUploadConfirmed;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

final class PhotoDerivativeStepTask {
    private static final int READY_TO_SUBMIT = 0;
    private static final int WAITING_FOR_TASK = 10;
    private static final int BACKING_OFF = 20;

    private final String stepId;
    private final PhotoUploadConfirmed event;
    private final Executor executor;
    private final PhotoDerivativeProperties properties;

    private CompletableFuture<Void> future;
    private int attempt;

    PhotoDerivativeStepTask(
            String stepId,
            PhotoUploadConfirmed event,
            Executor executor,
            PhotoDerivativeProperties properties
    ) {
        this.stepId = stepId;
        this.event = event;
        this.executor = executor;
        this.properties = properties;
    }

    StepResult tick(StepContext ctx, Action action) {
        return switch (ctx.stepNo()) {
            case READY_TO_SUBMIT -> submit(ctx, action);
            case WAITING_FOR_TASK -> observe(ctx);
            case BACKING_OFF -> waitBackoff(ctx);
            default -> StepResult.fail(new IllegalStateException(
                    "Unknown photo derivative stepNo: " + ctx.stepNo()));
        };
    }

    void dispose() {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
        future = null;
    }

    private StepResult submit(StepContext ctx, Action action) {
        attempt++;
        future = CompletableFuture.runAsync(() -> run(action), executor);
        ctx.startTimeout(properties.safeStepTimeoutMs());
        ctx.setStepNo(WAITING_FOR_TASK);
        return StepResult.stay();
    }

    private StepResult observe(StepContext ctx) {
        if (future == null) {
            ctx.setStepNo(READY_TO_SUBMIT);
            return StepResult.stay();
        }
        if (future.isDone()) {
            var failure = failureOf(future);
            future = null;
            if (failure == null) {
                return StepResult.done();
            }
            return handleFailure(ctx, failure);
        }
        if (ctx.timedOut()) {
            future.cancel(true);
            future = null;
            return handleFailure(ctx, new TimeoutException(
                    stepId + " timed out after " + properties.safeStepTimeoutMs() + "ms"));
        }
        return StepResult.stay();
    }

    private StepResult waitBackoff(StepContext ctx) {
        if (!ctx.timedOut()) {
            return StepResult.stay();
        }
        ctx.setStepNo(READY_TO_SUBMIT);
        return StepResult.stay();
    }

    private StepResult handleFailure(StepContext ctx, Throwable cause) {
        if (attempt >= properties.safeMaxAttempts()) {
            ctx.eventBus().publish(new PhotoDerivativeGenerationFailed(
                    event.officeId(),
                    event.photoId(),
                    stepId,
                    attempt,
                    reasonOf(cause),
                    now(ctx)));
            return StepResult.fail(cause);
        }

        ctx.startTimeout(properties.retryDelayMs(attempt));
        ctx.setStepNo(BACKING_OFF);
        return StepResult.stay();
    }

    private static void run(Action action) {
        try {
            action.run();
        } catch (Exception ex) {
            throw new CompletionException(ex);
        }
    }

    private static Throwable failureOf(CompletableFuture<Void> completed) {
        try {
            completed.join();
            return null;
        } catch (CompletionException ex) {
            return unwrap(ex);
        } catch (CancellationException ex) {
            return ex;
        }
    }

    private static Throwable unwrap(CompletionException ex) {
        return ex.getCause() == null ? ex : ex.getCause();
    }

    private static String reasonOf(Throwable cause) {
        if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        return cause.getClass().getSimpleName();
    }

    private static OffsetDateTime now(StepContext ctx) {
        return OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(ctx.clock().currentTimeMillis()),
                ZoneOffset.UTC);
    }

    @FunctionalInterface
    interface Action {
        void run() throws Exception;
    }
}
