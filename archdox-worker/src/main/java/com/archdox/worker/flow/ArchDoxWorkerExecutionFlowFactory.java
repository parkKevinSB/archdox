package com.archdox.worker.flow;

import com.archdox.worker.application.ArchDoxWorkerActionRegistry;
import com.archdox.worker.application.ArchDoxWorkerPolicyGate;
import com.archdox.worker.application.ArchDoxWorkerTraceSink;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import com.archdox.worker.flow.step.ExecuteArchDoxWorkerActionStep;
import com.archdox.worker.flow.step.GateArchDoxWorkerActionStep;
import com.archdox.worker.flow.step.RecordArchDoxWorkerRequestStep;
import com.archdox.worker.flow.step.RecordArchDoxWorkerResultStep;
import com.archdox.worker.flow.step.ResolveArchDoxWorkerActionStep;
import io.github.parkkevinsb.flower.core.flow.Flow;
import java.util.Objects;

public class ArchDoxWorkerExecutionFlowFactory {
    public static final String FLOW_TYPE = "archdox-worker-execution";

    private final ArchDoxWorkerActionRegistry registry;
    private final ArchDoxWorkerPolicyGate policyGate;
    private final ArchDoxWorkerTraceSink traceSink;

    public ArchDoxWorkerExecutionFlowFactory(
            ArchDoxWorkerActionRegistry registry,
            ArchDoxWorkerPolicyGate policyGate,
            ArchDoxWorkerTraceSink traceSink
    ) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.policyGate = Objects.requireNonNull(policyGate, "policyGate must not be null");
        this.traceSink = Objects.requireNonNull(traceSink, "traceSink must not be null");
    }

    public Flow create(ArchDoxWorkerRequest request, ArchDoxWorkerAction action) {
        var session = new ArchDoxWorkerExecutionSession(request, action);
        return Flow.builder(FLOW_TYPE, request.requestId() + ":" + action.actionType())
                .step("record-worker-request", new RecordArchDoxWorkerRequestStep(traceSink, session))
                .step("resolve-worker-action", new ResolveArchDoxWorkerActionStep(registry, traceSink, session))
                .step("gate-worker-action", new GateArchDoxWorkerActionStep(policyGate, traceSink, session))
                .step("execute-worker-action", new ExecuteArchDoxWorkerActionStep(traceSink, session))
                .step("record-worker-result", new RecordArchDoxWorkerResultStep(traceSink, session))
                .build();
    }
}
