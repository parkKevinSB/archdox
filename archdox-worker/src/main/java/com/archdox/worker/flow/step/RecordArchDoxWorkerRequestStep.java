package com.archdox.worker.flow.step;

import com.archdox.worker.application.ArchDoxWorkerTraceEvent;
import com.archdox.worker.application.ArchDoxWorkerTraceEventType;
import com.archdox.worker.application.ArchDoxWorkerTraceSink;
import com.archdox.worker.flow.ArchDoxWorkerExecutionSession;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public class RecordArchDoxWorkerRequestStep extends Step {
    private final ArchDoxWorkerTraceSink traceSink;
    private final ArchDoxWorkerExecutionSession session;

    public RecordArchDoxWorkerRequestStep(ArchDoxWorkerTraceSink traceSink, ArchDoxWorkerExecutionSession session) {
        this.traceSink = traceSink;
        this.session = session;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        traceSink.record(ArchDoxWorkerTraceEvent.of(
                ArchDoxWorkerTraceEventType.REQUEST_RECEIVED,
                session.request(),
                session.action(),
                "WORKER_REQUEST_RECEIVED",
                "Worker request received"));
        return StepResult.done();
    }
}
