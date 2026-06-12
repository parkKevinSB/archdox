package com.archdox.cloud.legal.application;

import com.archdox.cloud.aipolicy.application.AiModelCallMetadata;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.legal.flow.LegalDigestAiWorker;
import com.archdox.legalai.LegalDigestHarnessFactory;
import com.archdox.legalai.LegalDigestResult;
import com.archdox.worker.application.ArchDoxWorkerAsyncActionExecutor;
import com.archdox.worker.application.ArchDoxWorkerActionExecutor;
import com.archdox.worker.application.ArchDoxWorkerExecutionContext;
import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlowFactory;
import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.refine.MaxAttemptsRefinePolicy;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStatus;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStore;
import io.github.parkkevinsb.flower.ai.harness.spi.TraceListener;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LegalDigestEnrichmentArchDoxWorkerActionExecutor implements ArchDoxWorkerAsyncActionExecutor {
    private static final Duration WAIT_GRACE = Duration.ofSeconds(3);

    private final LegalDigestAiInputService inputService;
    private final AiHarnessPolicyExecutionService aiHarnessPolicyExecutionService;
    private final LegalDigestAiWorker aiWorker;
    private final AiModelGateway aiModelGateway;
    private final ObjectMapper objectMapper;
    private final TraceListener aiHarnessTraceListener;
    private final Executor workerActionExecutor;

    public LegalDigestEnrichmentArchDoxWorkerActionExecutor(
            LegalDigestAiInputService inputService,
            AiHarnessPolicyExecutionService aiHarnessPolicyExecutionService,
            LegalDigestAiWorker aiWorker,
            AiModelGateway aiModelGateway,
            ObjectMapper objectMapper,
            TraceListener aiHarnessTraceListener,
            @Qualifier("archDoxWorkerActionExecutor") Executor workerActionExecutor
    ) {
        this.inputService = inputService;
        this.aiHarnessPolicyExecutionService = aiHarnessPolicyExecutionService;
        this.aiWorker = aiWorker;
        this.aiModelGateway = aiModelGateway;
        this.objectMapper = objectMapper;
        this.aiHarnessTraceListener = aiHarnessTraceListener;
        this.workerActionExecutor = workerActionExecutor;
    }

    @Override
    public ArchDoxWorkerActionType actionType() {
        return ArchDoxWorkerActionType.ENRICH_LEGAL_CHANGE_DIGEST;
    }

    @Override
    public CompletableFuture<ArchDoxWorkerActionResult> executeAsync(ArchDoxWorkerExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> prepareExecution(context), workerActionExecutor)
                .thenCompose(Function.identity());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    private CompletableFuture<ArchDoxWorkerActionResult> prepareExecution(ArchDoxWorkerExecutionContext context) {
        var payload = context.action().payload();
        if (!booleanValue(payload.get("dryRun"))) {
            return CompletableFuture.completedFuture(ArchDoxWorkerActionResult.rejected(
                    "LEGAL_DIGEST_AI_DRAFT_DRY_RUN_REQUIRED",
                    "Legal digest AI enrichment is currently available only as a dry-run draft."));
        }
        var policy = aiHarnessPolicyExecutionService.resolve(AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT);
        if (!policy.runnable()) {
            return CompletableFuture.completedFuture(ArchDoxWorkerActionResult.failed(
                    "LEGAL_DIGEST_AI_NOT_CONFIGURED",
                    "Legal digest AI draft generation is not runnable: " + policy.unavailableReason()));
        }
        var changeSetId = longValue(payload.get("changeSetId"));
        if (changeSetId == null) {
            return CompletableFuture.completedFuture(ArchDoxWorkerActionResult.failed(
                    "LEGAL_DIGEST_CHANGE_SET_REQUIRED",
                    "Legal digest AI draft generation requires changeSetId."));
        }

        var input = inputService.buildInput(changeSetId);
        var plan = policy.plan();
        try {
            aiHarnessPolicyExecutionService.requireWithinBudget(plan);
        } catch (BadRequestException ex) {
            return CompletableFuture.completedFuture(ArchDoxWorkerActionResult.failed(ex.code(), ex.getMessage()));
        }
        var timeout = plan.timeout();
        var spec = new LegalDigestHarnessFactory(objectMapper).spec(
                (findings, ctx) -> {
                },
                AiHarnessRunStore.noop(),
                new MaxAttemptsRefinePolicy(plan.maxAttempts()),
                aiHarnessTraceListener);
        var flow = new AiHarnessFlowFactory<>(aiModelGateway, spec, Instant::now)
                .createFlow(input, AiHarnessFlowFactory.RunOverrides.builder()
                        .modelId(plan.modelId())
                        .timeout(timeout)
                        .providerOptions(AiModelCallMetadata.options(
                                context.request().context().officeId(),
                                context.request().context().userId(),
                                "LEGAL_DIGEST_ENRICHMENT",
                                "legal-digest-ai-draft",
                                "change-set:" + changeSetId,
                                "LEGAL_CHANGE_SET",
                                changeSetId,
                                Map.of(
                                        "archdox.workerRequestId", context.request().requestId().toString(),
                                        "archdox.legalDigestId", text(payload.get("digestId"))),
                                plan.maxOutputTokens()))
                        .build());
        return aiWorker.submitAndTrackAsync(flow, timeout.plus(WAIT_GRACE))
                .thenApply(awaited -> {
                    if (!awaited) {
                        return ArchDoxWorkerActionResult.failed(
                                "LEGAL_DIGEST_AI_TIMEOUT",
                                "Legal digest AI draft generation timed out.");
                    }
                    if (flow.context().status() != AiHarnessRunStatus.SUCCEEDED) {
                        return ArchDoxWorkerActionResult.failed(
                                "LEGAL_DIGEST_AI_HARNESS_FAILED",
                                flow.context().terminalReason().orElse("Legal digest AI harness did not succeed."));
                    }
                    var result = result(flow.context().latestValidation());
                    if (result.isEmpty()) {
                        return ArchDoxWorkerActionResult.failed(
                                "LEGAL_DIGEST_AI_RESULT_INVALID",
                                "Legal digest AI harness did not return a valid draft result.");
                    }
                    return ArchDoxWorkerActionResult.succeeded(output(
                            context,
                            changeSetId,
                            flow.context().runId().value(),
                            result.get()));
                });
    }

    private Map<String, Object> output(
            ArchDoxWorkerExecutionContext context,
            Long changeSetId,
            String aiHarnessRunId,
            LegalDigestResult result
    ) {
        var payload = context.action().payload();
        var output = new LinkedHashMap<String, Object>();
        output.put("dryRun", true);
        output.put("publicationApplied", false);
        output.put("corpusMutated", false);
        output.put("digestMutated", false);
        output.put("workerRequestId", context.request().requestId().toString());
        putIfPresent(output, "digestId", longValue(payload.get("digestId")));
        output.put("changeSetId", changeSetId);
        output.put("aiHarnessRunId", aiHarnessRunId);
        output.put("digestDraftStatus", result.status().name());
        output.put("title", result.title());
        output.put("summary", result.summary());
        output.put("impactSummary", result.impactSummary());
        output.put("confidence", result.confidence());
        output.put("affectedReportTypes", result.affectedReportTypes());
        output.put("affectedCatalogItems", result.affectedCatalogItems());
        output.put("keyArticles", result.keyArticles());
        output.put("reviewNotes", result.reviewNotes());
        return Map.copyOf(output);
    }

    private void putIfPresent(Map<String, Object> output, String key, Object value) {
        if (value != null) {
            output.put(key, value);
        }
    }

    private Optional<LegalDigestResult> result(Optional<ValidationResult<?>> validation) {
        return validation
                .filter(ValidationResult::isValid)
                .flatMap(value -> {
                    if (value instanceof ValidationResult.Valid<?> valid
                            && valid.value() instanceof LegalDigestResult result) {
                        return Optional.of(result);
                    }
                    return Optional.empty();
                });
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        return false;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
