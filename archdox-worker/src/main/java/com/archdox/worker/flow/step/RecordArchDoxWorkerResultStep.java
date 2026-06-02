package com.archdox.worker.flow.step;

import com.archdox.worker.application.ArchDoxWorkerTraceEvent;
import com.archdox.worker.application.ArchDoxWorkerTraceEventType;
import com.archdox.worker.application.ArchDoxWorkerTraceSink;
import com.archdox.worker.flow.ArchDoxWorkerExecutionSession;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import java.util.Map;

public class RecordArchDoxWorkerResultStep extends Step {
    private final ArchDoxWorkerTraceSink traceSink;
    private final ArchDoxWorkerExecutionSession session;

    public RecordArchDoxWorkerResultStep(ArchDoxWorkerTraceSink traceSink, ArchDoxWorkerExecutionSession session) {
        this.traceSink = traceSink;
        this.session = session;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        var result = session.result();
        if (result == null) {
            return StepResult.fail(new IllegalStateException("Worker action result is missing"));
        }
        var eventType = switch (result.status()) {
            case SUCCEEDED -> ArchDoxWorkerTraceEventType.ACTION_SUCCEEDED;
            case PENDING_APPROVAL -> ArchDoxWorkerTraceEventType.APPROVAL_REQUIRED;
            case REJECTED -> ArchDoxWorkerTraceEventType.ACTION_REJECTED;
            case FAILED -> ArchDoxWorkerTraceEventType.ACTION_FAILED;
        };
        traceSink.record(ArchDoxWorkerTraceEvent.of(
                eventType,
                session.request(),
                session.action(),
                result.resultCode(),
                result.message(),
                Map.of("status", result.status().name())));
        return StepResult.done();
    }
}
