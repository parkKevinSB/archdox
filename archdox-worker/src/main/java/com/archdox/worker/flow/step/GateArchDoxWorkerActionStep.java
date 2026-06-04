package com.archdox.worker.flow.step;

import com.archdox.worker.application.ArchDoxWorkerPolicyGate;
import com.archdox.worker.application.ArchDoxWorkerTraceEvent;
import com.archdox.worker.application.ArchDoxWorkerTraceEventType;
import com.archdox.worker.application.ArchDoxWorkerTraceSink;
import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.flow.ArchDoxWorkerExecutionSession;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public class GateArchDoxWorkerActionStep extends Step {
    private final ArchDoxWorkerPolicyGate policyGate;
    private final ArchDoxWorkerTraceSink traceSink;
    private final ArchDoxWorkerExecutionSession session;

    public GateArchDoxWorkerActionStep(
            ArchDoxWorkerPolicyGate policyGate,
            ArchDoxWorkerTraceSink traceSink,
            ArchDoxWorkerExecutionSession session
    ) {
        this.policyGate = policyGate;
        this.traceSink = traceSink;
        this.session = session;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        var decision = policyGate.evaluate(session.request(), session.action(), session.definition());
        session.policyDecision(decision);
        if (!decision.allowed()) {
            session.result(ArchDoxWorkerActionResult.rejected(decision.reasonCode(), decision.message()));
            traceSink.record(ArchDoxWorkerTraceEvent.of(
                    ArchDoxWorkerTraceEventType.POLICY_DENIED,
                    session.request(),
                    session.action(),
                    decision.reasonCode(),
                    decision.message()));
            return StepResult.goTo("record-worker-result");
        }
        if (decision.requiresApproval()) {
            session.result(ArchDoxWorkerActionResult.pendingApproval(decision.reasonCode(), decision.message()));
            traceSink.record(ArchDoxWorkerTraceEvent.of(
                    ArchDoxWorkerTraceEventType.APPROVAL_REQUIRED,
                    session.request(),
                    session.action(),
                    decision.reasonCode(),
                    decision.message()));
            return StepResult.goTo("record-worker-result");
        }
        traceSink.record(ArchDoxWorkerTraceEvent.of(
                ArchDoxWorkerTraceEventType.POLICY_ALLOWED,
                session.request(),
                session.action(),
                decision.reasonCode(),
                decision.message()));
        return StepResult.done();
    }
}
