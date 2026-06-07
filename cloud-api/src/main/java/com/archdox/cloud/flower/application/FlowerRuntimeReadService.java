package com.archdox.cloud.flower.application;

import com.archdox.cloud.flower.dto.FlowerExecutionContextResponse;
import com.archdox.cloud.flower.dto.FlowerFlowResponse;
import com.archdox.cloud.flower.dto.FlowerFlowStepResponse;
import com.archdox.cloud.flower.dto.FlowerRuntimeDumpResponse;
import com.archdox.cloud.flower.dto.FlowerWorkerResponse;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.flow.FlowSnapshot;
import io.github.parkkevinsb.flower.core.flow.FlowStepSnapshot;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

@Service
public class FlowerRuntimeReadService {
    private final PlatformAdminService platformAdminService;
    private final Engine engine;

    public FlowerRuntimeReadService(PlatformAdminService platformAdminService, Engine engine) {
        this.platformAdminService = platformAdminService;
        this.engine = engine;
    }

    public FlowerRuntimeDumpResponse dump(UserPrincipal principal) {
        platformAdminService.requirePlatformAdmin(principal);
        var dump = engine.dump();
        var workers = dump.workers().stream()
                .map(worker -> new FlowerWorkerResponse(
                        worker.name(),
                        worker.state().name(),
                        worker.intervalMillis(),
                        worker.flows().size(),
                        worker.flows().stream().map(this::flow).toList()))
                .toList();
        var activeFlowCount = workers.stream()
                .mapToInt(FlowerWorkerResponse::activeFlowCount)
                .sum();
        return new FlowerRuntimeDumpResponse(
                dump.engineState().name(),
                OffsetDateTime.now(),
                workers.size(),
                activeFlowCount,
                workers);
    }

    private FlowerFlowResponse flow(FlowSnapshot snapshot) {
        var flowId = snapshot.flowId();
        var failure = snapshot.failureCause();
        return new FlowerFlowResponse(
                flowId.flowType(),
                flowId.flowKey(),
                snapshot.state().name(),
                snapshot.currentStepId(),
                snapshot.currentStepIndex(),
                snapshot.currentStepNo(),
                failure == null ? "" : failure.getClass().getName(),
                failure == null ? "" : text(failure.getMessage()),
                executionContext(snapshot.executionContext()),
                snapshot.steps().stream()
                        .map(step -> step(step, snapshot.currentStepIndex()))
                        .toList());
    }

    private FlowerFlowStepResponse step(FlowStepSnapshot step, int currentStepIndex) {
        return new FlowerFlowStepResponse(
                step.index(),
                step.stepId(),
                compactStepType(step.stepType()),
                step.index() == currentStepIndex,
                step.guarded(),
                step.recoverable(),
                text(step.recoveryPolicy()));
    }

    private FlowerExecutionContextResponse executionContext(ExecutionContext context) {
        var safeContext = context == null ? ExecutionContext.empty() : context;
        return new FlowerExecutionContextResponse(
                safeContext.tenantIdOrNull(),
                safeContext.userIdOrNull(),
                safeContext.sessionIdOrNull(),
                safeContext.runIdOrNull(),
                safeContext.traceIdOrNull(),
                safeContext.correlationIdOrNull());
    }

    private String compactStepType(String stepType) {
        var value = text(stepType);
        var index = value.lastIndexOf('.');
        return index >= 0 ? value.substring(index + 1) : value;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
