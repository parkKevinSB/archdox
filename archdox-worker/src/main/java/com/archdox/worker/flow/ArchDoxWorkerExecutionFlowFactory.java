package com.archdox.worker.flow;

import com.archdox.worker.application.ArchDoxWorkerActionRegistry;
import com.archdox.worker.application.ArchDoxWorkerPolicyGate;
import com.archdox.worker.application.ArchDoxWorkerRunControl;
import com.archdox.worker.application.ArchDoxWorkerTraceSink;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import com.archdox.worker.flow.step.CheckArchDoxWorkerRunControlStep;
import com.archdox.worker.flow.step.ExecuteArchDoxWorkerActionStep;
import com.archdox.worker.flow.step.GateArchDoxWorkerActionStep;
import com.archdox.worker.flow.step.RecordArchDoxWorkerRequestStep;
import com.archdox.worker.flow.step.RecordArchDoxWorkerResultStep;
import com.archdox.worker.flow.step.ResolveArchDoxWorkerActionStep;
import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.flow.Flow;
import java.util.Objects;

public class ArchDoxWorkerExecutionFlowFactory {
    public static final String FLOW_TYPE = "archdox-worker-execution";

    private final ArchDoxWorkerActionRegistry registry;
    private final ArchDoxWorkerPolicyGate policyGate;
    private final ArchDoxWorkerRunControl runControl;
    private final ArchDoxWorkerTraceSink traceSink;

    public ArchDoxWorkerExecutionFlowFactory(
            ArchDoxWorkerActionRegistry registry,
            ArchDoxWorkerPolicyGate policyGate,
            ArchDoxWorkerTraceSink traceSink
    ) {
        this(registry, policyGate, ArchDoxWorkerRunControl.allowAll(), traceSink);
    }

    public ArchDoxWorkerExecutionFlowFactory(
            ArchDoxWorkerActionRegistry registry,
            ArchDoxWorkerPolicyGate policyGate,
            ArchDoxWorkerRunControl runControl,
            ArchDoxWorkerTraceSink traceSink
    ) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.policyGate = Objects.requireNonNull(policyGate, "policyGate must not be null");
        this.runControl = Objects.requireNonNull(runControl, "runControl must not be null");
        this.traceSink = Objects.requireNonNull(traceSink, "traceSink must not be null");
    }

    public Flow create(ArchDoxWorkerRequest request, ArchDoxWorkerAction action) {
        return createHandle(request, action).flow();
    }

    public ArchDoxWorkerExecutionHandle createHandle(ArchDoxWorkerRequest request, ArchDoxWorkerAction action) {
        var session = new ArchDoxWorkerExecutionSession(request, action);
        var flow = Flow.builder(FLOW_TYPE, request.requestId() + ":" + action.actionType())
                .step("record-worker-request", new RecordArchDoxWorkerRequestStep(traceSink, session))
                .step("resolve-worker-action", new ResolveArchDoxWorkerActionStep(registry, traceSink, session))
                .step("gate-worker-action", new GateArchDoxWorkerActionStep(policyGate, traceSink, session))
                .step("check-worker-run-control", new CheckArchDoxWorkerRunControlStep(runControl, session))
                .step("execute-worker-action", new ExecuteArchDoxWorkerActionStep(traceSink, session))
                .step("record-worker-result", new RecordArchDoxWorkerResultStep(traceSink, session))
                .executionContext(executionContext(request))
                .build();
        return new ArchDoxWorkerExecutionHandle(flow, session);
    }

    private ExecutionContext executionContext(ArchDoxWorkerRequest request) {
        var context = request.context();
        var builder = ExecutionContext.builder()
                .runId(request.requestId().toString())
                .correlationId(request.requestId().toString());
        if (context.officeId() != null) {
            builder.tenantId(context.officeId().toString());
        }
        if (context.userId() != null) {
            builder.userId(context.userId().toString());
        }
        return builder.build();
    }
}
