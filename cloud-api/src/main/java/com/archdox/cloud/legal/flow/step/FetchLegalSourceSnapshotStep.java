package com.archdox.cloud.legal.flow.step;

import com.archdox.cloud.legal.application.LegalCorpusSyncService;
import com.archdox.cloud.legal.application.LegalSourceSnapshot;
import com.archdox.cloud.legal.application.LawOpenDataException;
import com.archdox.cloud.legal.event.LegalSyncRequested;
import com.archdox.cloud.legal.flow.LegalSyncSession;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class FetchLegalSourceSnapshotStep extends Step {
    private static final int READY_TO_SUBMIT = 0;
    private static final int WAITING_FOR_SNAPSHOT = 10;

    private final LegalCorpusSyncService syncService;
    private final LegalSyncRequested event;
    private final LegalSyncSession session;
    private CompletableFuture<LegalSourceSnapshot> snapshotFuture;

    public FetchLegalSourceSnapshotStep(
            LegalCorpusSyncService syncService,
            LegalSyncRequested event,
            LegalSyncSession session
    ) {
        this.syncService = syncService;
        this.event = event;
        this.session = session;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        return switch (ctx.stepNo()) {
            case READY_TO_SUBMIT -> submit(ctx);
            case WAITING_FOR_SNAPSHOT -> observe(ctx);
            default -> StepResult.fail(new IllegalStateException(
                    "Unknown legal sync fetch stepNo: " + ctx.stepNo()));
        };
    }

    @Override
    protected void onExit(StepContext ctx) {
        cancelPendingSnapshot();
    }

    @Override
    protected void onReset(StepContext ctx) {
        cancelPendingSnapshot();
    }

    private StepResult submit(StepContext ctx) {
        try {
            snapshotFuture = syncService.fetchSnapshotAsync(event.sourceCode());
            ctx.setStepNo(WAITING_FOR_SNAPSHOT);
            return StepResult.stay();
        } catch (RuntimeException ex) {
            var failure = unwrap(ex);
            syncService.markRunFailed(event.syncRunId(), failureCode(failure), failure.getMessage());
            return StepResult.fail(failure);
        }
    }

    private StepResult observe(StepContext ctx) {
        if (snapshotFuture == null) {
            ctx.setStepNo(READY_TO_SUBMIT);
            return StepResult.stay();
        }
        if (!snapshotFuture.isDone()) {
            return StepResult.stay();
        }
        try {
            session.snapshot(snapshotFuture.join());
            snapshotFuture = null;
            return StepResult.done();
        } catch (RuntimeException ex) {
            var failure = unwrap(ex);
            syncService.markRunFailed(event.syncRunId(), failureCode(failure), failure.getMessage());
            snapshotFuture = null;
            return StepResult.fail(failure);
        }
    }

    private String failureCode(RuntimeException ex) {
        if (ex instanceof LawOpenDataException openDataException) {
            return openDataException.code();
        }
        return ex.getClass().getSimpleName();
    }

    private RuntimeException unwrap(RuntimeException ex) {
        if (ex instanceof CompletionException completionException
                && completionException.getCause() instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (ex instanceof CancellationException) {
            return ex;
        }
        return ex;
    }

    private void cancelPendingSnapshot() {
        if (snapshotFuture != null && !snapshotFuture.isDone()) {
            snapshotFuture.cancel(true);
        }
        snapshotFuture = null;
    }
}
