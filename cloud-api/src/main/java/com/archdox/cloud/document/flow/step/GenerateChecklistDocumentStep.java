package com.archdox.cloud.document.flow.step;

import com.archdox.cloud.document.application.DocumentGenerationException;
import com.archdox.cloud.document.application.DocumentGenerationProperties;
import com.archdox.cloud.document.application.DocumentJobService;
import com.archdox.cloud.document.event.DocumentGenerationFailedEvent;
import com.archdox.cloud.document.event.DocumentGenerationRequested;
import io.github.parkkevinsb.flower.core.step.Step;
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

public final class GenerateChecklistDocumentStep extends Step {
    private static final int CHECK_ROUTE = 0;
    private static final int WAITING_FOR_TASK = 10;
    private static final int BACKING_OFF = 20;

    private final DocumentJobService documentJobService;
    private final DocumentGenerationRequested event;
    private final Executor executor;
    private final DocumentGenerationProperties properties;

    private CompletableFuture<Void> future;
    private int attempt;

    public GenerateChecklistDocumentStep(
            DocumentJobService documentJobService,
            DocumentGenerationRequested event,
            Executor executor,
            DocumentGenerationProperties properties
    ) {
        this.documentJobService = documentJobService;
        this.event = event;
        this.executor = executor;
        this.properties = properties;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        return switch (ctx.stepNo()) {
            case CHECK_ROUTE -> checkRoute(ctx);
            case WAITING_FOR_TASK -> observe(ctx);
            case BACKING_OFF -> waitBackoff(ctx);
            default -> StepResult.fail(new IllegalStateException(
                    "Unknown checklist document generation stepNo: " + ctx.stepNo()));
        };
    }

    private StepResult checkRoute(StepContext ctx) {
        if (!documentJobService.isCloudApiChecklistDocument(event.officeId(), event.documentJobId())) {
            return StepResult.done();
        }
        attempt++;
        future = CompletableFuture.runAsync(() -> documentJobService.completeCloudApiChecklistDocument(
                event.officeId(),
                event.documentJobId()), executor);
        ctx.startTimeout(properties.safeStepTimeoutMs());
        ctx.setStepNo(WAITING_FOR_TASK);
        return StepResult.stay();
    }

    private StepResult observe(StepContext ctx) {
        if (future == null) {
            ctx.setStepNo(CHECK_ROUTE);
            return StepResult.stay();
        }
        if (future.isDone()) {
            var failure = failureOf(future);
            future = null;
            if (failure == null) {
                return StepResult.finish();
            }
            return handleFailure(ctx, failure);
        }
        if (ctx.timedOut()) {
            future.cancel(true);
            future = null;
            return handleFailure(ctx, new TimeoutException(
                    "generate-checklist-document timed out after " + properties.safeStepTimeoutMs() + "ms"));
        }
        return StepResult.stay();
    }

    private StepResult waitBackoff(StepContext ctx) {
        if (!ctx.timedOut()) {
            return StepResult.stay();
        }
        ctx.setStepNo(CHECK_ROUTE);
        return StepResult.stay();
    }

    private StepResult handleFailure(StepContext ctx, Throwable cause) {
        if (attempt >= properties.safeMaxAttempts()) {
            var errorCode = cause instanceof DocumentGenerationException ex
                    ? ex.errorCode()
                    : "CHECKLIST_DOCUMENT_GENERATION_FAILED";
            var reason = reasonOf(cause);
            documentJobService.markGenerationFailed(
                    event.officeId(),
                    event.documentJobId(),
                    errorCode,
                    reason);
            ctx.eventBus().publish(new DocumentGenerationFailedEvent(
                    event.officeId(),
                    event.reportId(),
                    event.documentJobId(),
                    ctx.currentStepId(),
                    attempt,
                    reason,
                    now(ctx)));
            return StepResult.fail(cause);
        }
        ctx.startTimeout(properties.retryDelayMs(attempt));
        ctx.setStepNo(BACKING_OFF);
        return StepResult.stay();
    }

    @Override
    protected void onExit(StepContext ctx) {
        dispose();
    }

    @Override
    protected void onReset(StepContext ctx) {
        dispose();
    }

    private void dispose() {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
        future = null;
    }

    private static Throwable failureOf(CompletableFuture<Void> completed) {
        try {
            completed.join();
            return null;
        } catch (CompletionException ex) {
            return ex.getCause() == null ? ex : ex.getCause();
        } catch (CancellationException ex) {
            return ex;
        }
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
}
