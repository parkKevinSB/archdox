package com.archdox.cloud.flower.application;

import com.archdox.cloud.flower.dto.FlowerExecutionContextResponse;
import com.archdox.cloud.flower.dto.FlowerExecutorResponse;
import com.archdox.cloud.flower.dto.FlowerFlowResponse;
import com.archdox.cloud.flower.dto.FlowerFlowStepResponse;
import com.archdox.cloud.flower.dto.FlowerRuntimeDumpResponse;
import com.archdox.cloud.flower.dto.FlowerWorkerResponse;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.infra.OperationEventRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.flow.FlowSnapshot;
import io.github.parkkevinsb.flower.core.flow.FlowStepSnapshot;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class FlowerRuntimeReadService {
    private static final List<String> OVERLOAD_EVENT_TYPES = List.of(
            "AI_MODEL_GATEWAY_QUEUE_FULL",
            "AGENT_COMMAND_QUEUE_FULL",
            "ARCHDOX_WORKER_ACTION_QUEUE_FULL",
            "LEGAL_SYNC_FETCH_QUEUE_FULL",
            "SERVER_RUNTIME_LOAD_HIGH");

    private final PlatformAdminService platformAdminService;
    private final Engine engine;
    private final Map<String, ExecutorService> executorServices;
    private final OperationEventRepository operationEventRepository;
    private final OperationEventService operationEventService;

    public FlowerRuntimeReadService(
            PlatformAdminService platformAdminService,
            Engine engine,
            Map<String, ExecutorService> executorServices,
            OperationEventRepository operationEventRepository,
            OperationEventService operationEventService
    ) {
        this.platformAdminService = platformAdminService;
        this.engine = engine;
        this.executorServices = Map.copyOf(executorServices);
        this.operationEventRepository = operationEventRepository;
        this.operationEventService = operationEventService;
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
        var executors = executorSnapshots();
        var queuedTaskCount = executors.stream().mapToInt(FlowerExecutorResponse::queueSize).sum();
        var saturatedExecutorCount = (int) executors.stream().filter(this::saturated).count();
        return new FlowerRuntimeDumpResponse(
                dump.engineState().name(),
                OffsetDateTime.now(),
                workers.size(),
                activeFlowCount,
                executors.size(),
                saturatedExecutorCount,
                queuedTaskCount,
                workers,
                executors,
                overloadEvents());
    }

    private List<FlowerExecutorResponse> executorSnapshots() {
        return executorServices.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof ThreadPoolExecutor)
                .map(entry -> executor(entry.getKey(), (ThreadPoolExecutor) entry.getValue()))
                .sorted(Comparator.comparing(FlowerExecutorResponse::beanName))
                .toList();
    }

    private FlowerExecutorResponse executor(String beanName, ThreadPoolExecutor executor) {
        var queue = executor.getQueue();
        var remainingCapacity = queue.remainingCapacity();
        return new FlowerExecutorResponse(
                beanName,
                executorState(executor),
                queue.getClass().getSimpleName(),
                executor.getCorePoolSize(),
                executor.getMaximumPoolSize(),
                executor.getPoolSize(),
                executor.getLargestPoolSize(),
                executor.getActiveCount(),
                queue.size(),
                remainingCapacity == Integer.MAX_VALUE ? null : remainingCapacity,
                executor.getTaskCount(),
                executor.getCompletedTaskCount(),
                executor.isShutdown(),
                executor.isTerminating(),
                executor.isTerminated());
    }

    private String executorState(ThreadPoolExecutor executor) {
        if (executor.isTerminated()) {
            return "TERMINATED";
        }
        if (executor.isTerminating()) {
            return "TERMINATING";
        }
        if (executor.isShutdown()) {
            return "SHUTDOWN";
        }
        if (executor.getActiveCount() >= executor.getMaximumPoolSize()
                && executor.getQueue().remainingCapacity() == 0) {
            return "SATURATED";
        }
        if (executor.getActiveCount() > 0 || executor.getQueue().size() > 0) {
            return "ACTIVE";
        }
        return "IDLE";
    }

    private boolean saturated(FlowerExecutorResponse executor) {
        return "SATURATED".equals(executor.state());
    }

    private List<com.archdox.cloud.operation.dto.OperationEventResponse> overloadEvents() {
        return operationEventRepository
                .findByEventTypeInOrderByCreatedAtDescIdDesc(OVERLOAD_EVENT_TYPES, PageRequest.of(0, 20))
                .stream()
                .map(operationEventService::toResponse)
                .toList();
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
