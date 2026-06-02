package com.archdox.worker.flow.step;

import com.archdox.worker.application.ArchDoxWorkerActionRegistry;
import com.archdox.worker.application.ArchDoxWorkerTraceEvent;
import com.archdox.worker.application.ArchDoxWorkerTraceEventType;
import com.archdox.worker.application.ArchDoxWorkerTraceSink;
import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.flow.ArchDoxWorkerExecutionSession;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public class ResolveArchDoxWorkerActionStep extends Step {
    private final ArchDoxWorkerActionRegistry registry;
    private final ArchDoxWorkerTraceSink traceSink;
    private final ArchDoxWorkerExecutionSession session;

    public ResolveArchDoxWorkerActionStep(
            ArchDoxWorkerActionRegistry registry,
            ArchDoxWorkerTraceSink traceSink,
            ArchDoxWorkerExecutionSession session
    ) {
        this.registry = registry;
        this.traceSink = traceSink;
        this.session = session;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        var executor = registry.resolve(session.action().actionType());
        if (executor.isEmpty()) {
            session.result(ArchDoxWorkerActionResult.rejected(
                    "WORKER_ACTION_NOT_REGISTERED",
                    "Worker action is not registered"));
            traceSink.record(ArchDoxWorkerTraceEvent.of(
                    ArchDoxWorkerTraceEventType.ACTION_UNKNOWN,
                    session.request(),
                    session.action(),
                    "WORKER_ACTION_NOT_REGISTERED",
                    "Worker action is not registered"));
            return StepResult.goTo("record-worker-result");
        }
        session.executor(executor.get());
        return StepResult.done();
    }
}
