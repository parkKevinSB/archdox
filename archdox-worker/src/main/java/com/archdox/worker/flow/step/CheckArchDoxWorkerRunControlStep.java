package com.archdox.worker.flow.step;

import com.archdox.worker.application.ArchDoxWorkerRunControl;
import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.flow.ArchDoxWorkerExecutionSession;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public class CheckArchDoxWorkerRunControlStep extends Step {
    private final ArchDoxWorkerRunControl runControl;
    private final ArchDoxWorkerExecutionSession session;

    public CheckArchDoxWorkerRunControlStep(
            ArchDoxWorkerRunControl runControl,
            ArchDoxWorkerExecutionSession session
    ) {
        this.runControl = runControl;
        this.session = session;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        if (session.hasTerminalResult()) {
            return StepResult.done();
        }
        try {
            var decision = runControl.check(session.request(), session.action(), session.definition());
            if (decision == null) {
                session.result(ArchDoxWorkerActionResult.failed(
                        "ARCHDOX_WORKER_RUN_CONTROL_DECISION_MISSING",
                        "Worker run-control decision is missing."));
                return StepResult.goTo("record-worker-result");
            }
            if (decision.allowed()) {
                return StepResult.done();
            }
            session.result(ArchDoxWorkerActionResult.cancelled(decision.reasonCode(), decision.message()));
            return StepResult.goTo("record-worker-result");
        } catch (RuntimeException ex) {
            session.result(ArchDoxWorkerActionResult.failed(
                    "ARCHDOX_WORKER_RUN_CONTROL_FAILED",
                    ex.getMessage()));
            return StepResult.goTo("record-worker-result");
        }
    }
}
