package com.archdox.worker.flow.step;

import com.archdox.worker.application.ArchDoxWorkerExecutionContext;
import com.archdox.worker.application.ArchDoxWorkerTraceEvent;
import com.archdox.worker.application.ArchDoxWorkerTraceEventType;
import com.archdox.worker.application.ArchDoxWorkerTraceSink;
import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.flow.ArchDoxWorkerExecutionSession;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public class ExecuteArchDoxWorkerActionStep extends Step {
    private final ArchDoxWorkerTraceSink traceSink;
    private final ArchDoxWorkerExecutionSession session;

    public ExecuteArchDoxWorkerActionStep(ArchDoxWorkerTraceSink traceSink, ArchDoxWorkerExecutionSession session) {
        this.traceSink = traceSink;
        this.session = session;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        if (session.hasTerminalResult()) {
            return StepResult.done();
        }
        traceSink.record(ArchDoxWorkerTraceEvent.of(
                ArchDoxWorkerTraceEventType.ACTION_STARTED,
                session.request(),
                session.action(),
                "WORKER_ACTION_STARTED",
                "Worker action started"));
        try {
            var result = session.executor().execute(new ArchDoxWorkerExecutionContext(
                    session.request(),
                    session.action(),
                    session.definition()));
            session.result(result);
            var eventType = switch (result.status()) {
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
                    result.resultCode(),
                    result.message()));
        } catch (RuntimeException ex) {
            var result = ArchDoxWorkerActionResult.failed("WORKER_ACTION_EXCEPTION", ex.getMessage());
            session.result(result);
            traceSink.record(ArchDoxWorkerTraceEvent.of(
                    ArchDoxWorkerTraceEventType.ACTION_FAILED,
                    session.request(),
                    session.action(),
                    result.resultCode(),
                    result.message()));
        }
        return StepResult.done();
    }
}
