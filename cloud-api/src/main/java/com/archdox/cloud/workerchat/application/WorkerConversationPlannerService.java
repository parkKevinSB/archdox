package com.archdox.cloud.workerchat.application;

import com.archdox.cloud.aipolicy.application.AiFeature;
import com.archdox.cloud.aipolicy.application.AiModelCallMetadata;
import com.archdox.cloud.aipolicy.application.AiPolicyExecutionService;
import com.archdox.workerai.ConversationPlannerHarnessFactory;
import com.archdox.workerai.ConversationPlannerInput;
import com.archdox.workerai.ConversationPlannerResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlowFactory;
import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.refine.MaxAttemptsRefinePolicy;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStatus;
import io.github.parkkevinsb.flower.ai.harness.spi.TraceListener;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerConversationPlannerService {
    private static final Duration PLANNER_TIMEOUT = Duration.ofSeconds(20);

    private final AiPolicyExecutionService aiPolicyExecutionService;
    private final AiModelGateway aiModelGateway;
    private final ArchDoxWorkerPlannerAiWorker plannerAiWorker;
    private final ObjectMapper objectMapper;
    private final TraceListener aiHarnessTraceListener;

    public WorkerConversationPlannerService(
            AiPolicyExecutionService aiPolicyExecutionService,
            AiModelGateway aiModelGateway,
            ArchDoxWorkerPlannerAiWorker plannerAiWorker,
            ObjectMapper objectMapper,
            TraceListener aiHarnessTraceListener
    ) {
        this.aiPolicyExecutionService = aiPolicyExecutionService;
        this.aiModelGateway = aiModelGateway;
        this.plannerAiWorker = plannerAiWorker;
        this.objectMapper = objectMapper;
        this.aiHarnessTraceListener = aiHarnessTraceListener;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompletableFuture<Optional<ConversationPlannerResult>> planAsync(
            Long officeId,
            Long userId,
            Long sessionId,
            ConversationPlannerInput input
    ) {
        if (input == null || input.userMessage().isBlank() || input.availableActions().isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        var aiPlan = aiPolicyExecutionService.findAllowed(officeId, userId, AiFeature.DOCUMENT_GENERATION)
                .or(() -> aiPolicyExecutionService.findAllowed(officeId, userId, AiFeature.DOCUMENT_REVIEW));
        if (aiPlan.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        var spec = new ConversationPlannerHarnessFactory(objectMapper).spec(
                (findings, ctx) -> {
                },
                io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStore.noop(),
                new MaxAttemptsRefinePolicy(2),
                aiHarnessTraceListener);
        var overrides = AiHarnessFlowFactory.RunOverrides.builder()
                .modelId(aiPlan.get().modelId())
                .timeout(PLANNER_TIMEOUT)
                .providerOptions(AiModelCallMetadata.options(
                        officeId,
                        aiPlan.get().userId(),
                        "WORKER_CONVERSATION_PLANNER",
                        "archdox-worker-chat-planner",
                        "worker-chat-session:" + sessionId,
                        "ARCHDOX_WORKER_CHAT_SESSION",
                        sessionId,
                        Map.of("archdox.projectId", input.projectId()),
                        aiPlan.get().maxOutputTokens()))
                .build();
        var flow = new AiHarnessFlowFactory<>(aiModelGateway, spec, Instant::now)
                .createFlow(input, overrides);
        return plannerAiWorker.submitAndTrackAsync(flow, PLANNER_TIMEOUT.plusSeconds(2))
                .thenApply(completed -> completed ? validatedResult(flow) : Optional.<ConversationPlannerResult>empty())
                .exceptionally(ex -> Optional.empty());
    }

    private Optional<ConversationPlannerResult> validatedResult(AiHarnessFlow flow) {
        if (flow.context().status() != AiHarnessRunStatus.SUCCEEDED) {
            return Optional.empty();
        }
        return flow.context().latestValidation()
                .filter(ValidationResult::isValid)
                .flatMap(validation -> {
                    if (validation instanceof ValidationResult.Valid<?> valid
                            && valid.value() instanceof ConversationPlannerResult result) {
                        return Optional.of(result);
                    }
                    return Optional.empty();
                });
    }
}
