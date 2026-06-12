package com.archdox.worker.flow.step;

import com.archdox.worker.application.ArchDoxWorkerAsyncActionExecutor;
import com.archdox.worker.application.ArchDoxWorkerExecutionContext;
import com.archdox.worker.application.ArchDoxWorkerTraceEvent;
import com.archdox.worker.application.ArchDoxWorkerTraceEventType;
import com.archdox.worker.application.ArchDoxWorkerTraceSink;
import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.flow.ArchDoxWorkerExecutionSession;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class ExecuteArchDoxWorkerActionStep extends Step {
    private static final int READY_TO_EXECUTE = 0;
    private static final int WAITING_FOR_ASYNC_RESULT = 10;

    private final ArchDoxWorkerTraceSink traceSink;
    private final ArchDoxWorkerExecutionSession session;
    private CompletableFuture<ArchDoxWorkerActionResult> asyncResult;

    public ExecuteArchDoxWorkerActionStep(ArchDoxWorkerTraceSink traceSink, ArchDoxWorkerExecutionSession session) {
        this.traceSink = traceSink;
        this.session = session;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        if (session.hasTerminalResult()) {
            return StepResult.done();
        }
        return switch (ctx.stepNo()) {
            case READY_TO_EXECUTE -> execute(ctx);
            case WAITING_FOR_ASYNC_RESULT -> observeAsyncResult(ctx);
            default -> StepResult.fail(new IllegalStateException(
                    "Unknown ArchDox worker action execution stepNo: " + ctx.stepNo()));
        };
    }

    @Override
    protected void onExit(StepContext ctx) {
        cancelPendingAsyncResult();
    }

    @Override
    protected void onReset(StepContext ctx) {
        cancelPendingAsyncResult();
    }

    private StepResult execute(StepContext ctx) {
        traceSink.record(ArchDoxWorkerTraceEvent.of(
                ArchDoxWorkerTraceEventType.ACTION_STARTED,
                session.request(),
                session.action(),
                "WORKER_ACTION_STARTED",
                "Worker action started"));
        var executionContext = new ArchDoxWorkerExecutionContext(
                session.request(),
                session.action(),
                session.definition());
        try {
            if (session.executor() instanceof ArchDoxWorkerAsyncActionExecutor asyncExecutor) {
                asyncResult = asyncExecutor.executeAsync(executionContext);
                if (asyncResult == null) {
                    complete(ArchDoxWorkerActionResult.failed(
                            "WORKER_ACTION_ASYNC_RESULT_MISSING",
                            "Worker async action executor returned no result future."));
                    return StepResult.done();
                }
                ctx.setStepNo(WAITING_FOR_ASYNC_RESULT);
                return StepResult.stay();
            }
            complete(session.executor().execute(executionContext));
        } catch (RuntimeException ex) {
            complete(ArchDoxWorkerActionResult.failed("WORKER_ACTION_EXCEPTION", ex.getMessage()));
        }
        return StepResult.done();
    }

    private StepResult observeAsyncResult(StepContext ctx) {
        if (asyncResult == null) {
            ctx.setStepNo(READY_TO_EXECUTE);
            return StepResult.stay();
        }
        if (!asyncResult.isDone()) {
            return StepResult.stay();
        }
        try {
            var result = asyncResult.join();
            asyncResult = null;
            complete(result == null
                    ? ArchDoxWorkerActionResult.failed(
                            "WORKER_ACTION_ASYNC_RESULT_MISSING",
                            "Worker async action executor completed without a result.")
                    : result);
        } catch (RuntimeException ex) {
            asyncResult = null;
            var failure = unwrap(ex);
            complete(ArchDoxWorkerActionResult.failed("WORKER_ACTION_EXCEPTION", failure.getMessage()));
        }
        return StepResult.done();
    }

    private void complete(ArchDoxWorkerActionResult result) {
        var safeResult = result == null
                ? ArchDoxWorkerActionResult.failed(
                        "WORKER_ACTION_RESULT_MISSING",
                        "Worker action executor returned no result.")
                : result;
        session.result(safeResult);
        var eventType = switch (safeResult.status()) {
            case SUCCEEDED -> ArchDoxWorkerTraceEventType.ACTION_SUCCEEDED;
            case PENDING_APPROVAL -> ArchDoxWorkerTraceEventType.APPROVAL_REQUIRED;
            case REJECTED -> ArchDoxWorkerTraceEventType.ACTION_REJECTED;
            case CANCELLED -> ArchDoxWorkerTraceEventType.ACTION_CANCELLED;
            case FAILED -> ArchDoxWorkerTraceEventType.ACTION_FAILED;
        };
        traceSink.record(ArchDoxWorkerTraceEvent.of(
                eventType,
                session.request(),
                session.action(),
                safeResult.resultCode(),
                safeResult.message()));
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

    private void cancelPendingAsyncResult() {
        if (asyncResult != null && !asyncResult.isDone()) {
            asyncResult.cancel(true);
        }
        asyncResult = null;
    }
}
