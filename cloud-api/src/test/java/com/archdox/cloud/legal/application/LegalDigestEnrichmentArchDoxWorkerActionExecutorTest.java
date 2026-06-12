package com.archdox.cloud.legal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.aipolicy.application.AiHarnessExecutionPlan;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyResolution;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.legal.flow.LegalDigestAiWorker;
import com.archdox.legalai.LegalDigestArticleChange;
import com.archdox.legalai.LegalDigestInput;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerActionExecutionStatus;
import com.archdox.worker.domain.ArchDoxWorkerActionOrigin;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import com.archdox.worker.domain.ArchDoxWorkerRequestContext;
import com.archdox.worker.domain.ArchDoxWorkerRequestSource;
import com.archdox.worker.application.ArchDoxWorkerExecutionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCall;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCallStatus;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import io.github.parkkevinsb.flower.ai.harness.spi.TraceListener;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.worker.Worker;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LegalDigestEnrichmentArchDoxWorkerActionExecutorTest {
    private final LegalDigestAiInputService inputService = mock(LegalDigestAiInputService.class);
    private final AiHarnessPolicyExecutionService policyExecutionService = mock(AiHarnessPolicyExecutionService.class);

    @Test
    void rejectsNonDryRunExecution() {
        var executor = executor(fakeGateway("{}"), new DirectLegalDigestAiWorker());

        var result = executor.execute(context(Map.of("changeSetId", 8L, "dryRun", false)));

        assertThat(result.status()).isEqualTo(ArchDoxWorkerActionExecutionStatus.REJECTED);
        assertThat(result.resultCode()).isEqualTo("LEGAL_DIGEST_AI_DRAFT_DRY_RUN_REQUIRED");
    }

    @Test
    void generatesDraftWithoutMutatingDigestOrCorpus() {
        when(inputService.buildInput(8L)).thenReturn(input());
        var executor = executor(fakeGateway("""
                {
                  "status": "NEEDS_HUMAN_REVIEW",
                  "title": "AI generated digest draft",
                  "summary": "The AI draft summarizes only the supplied legal corpus context.",
                  "impactSummary": "Review supervision reports and catalog guidance before publishing.",
                  "confidence": "MEDIUM",
                  "affectedReportTypes": ["CONSTRUCTION_DAILY_SUPERVISION_LOG"],
                  "affectedCatalogItems": ["CONSTRUCTION_SUPERVISION_LEGAL_CONTEXT"],
                  "keyArticles": ["0025001"],
                  "reviewNotes": "Admin review required before publication."
                }
                """), new DirectLegalDigestAiWorker());

        var result = executor.execute(context(Map.of("digestId", 4L, "changeSetId", 8L, "dryRun", true)));

        assertThat(result.status()).isEqualTo(ArchDoxWorkerActionExecutionStatus.SUCCEEDED);
        assertThat(result.output())
                .containsEntry("dryRun", true)
                .containsEntry("publicationApplied", false)
                .containsEntry("corpusMutated", false)
                .containsEntry("digestMutated", false)
                .containsEntry("digestId", 4L)
                .containsEntry("changeSetId", 8L)
                .containsEntry("digestDraftStatus", "NEEDS_HUMAN_REVIEW")
                .containsEntry("title", "AI generated digest draft");
        assertThat(result.output().get("aiHarnessRunId")).asString().isNotBlank();
        assertThat(result.output().get("keyArticles")).isEqualTo(List.of("0025001"));
        verify(inputService).buildInput(8L);
    }

    private LegalDigestEnrichmentArchDoxWorkerActionExecutor executor(
            AiModelGateway gateway,
            LegalDigestAiWorker worker
    ) {
        when(policyExecutionService.resolve(AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT))
                .thenReturn(AiHarnessPolicyResolution.runnable(new AiHarnessExecutionPlan(
                        AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT,
                        mock(AiProviderCredential.class),
                        new ModelId("fake-legal", "fake-legal-model"),
                        1,
                        Duration.ofSeconds(10))));
        return new LegalDigestEnrichmentArchDoxWorkerActionExecutor(
                inputService,
                policyExecutionService,
                worker,
                gateway,
                new ObjectMapper(),
                new TraceListener() {},
                Runnable::run);
    }

    private ArchDoxWorkerExecutionContext context(Map<String, Object> payload) {
        var request = new ArchDoxWorkerRequest(
                UUID.randomUUID(),
                ArchDoxWorkerRequestSource.UI,
                "test legal digest ai draft",
                new ArchDoxWorkerRequestContext(3L, null, null, null, null, null, "ko-KR"),
                Instant.now());
        var action = new ArchDoxWorkerAction(
                ArchDoxWorkerActionType.ENRICH_LEGAL_CHANGE_DIGEST,
                payload,
                "test",
                1.0d,
                ArchDoxWorkerActionOrigin.USER);
        return new ArchDoxWorkerExecutionContext(request, action);
    }

    private LegalDigestInput input() {
        return new LegalDigestInput(
                "8",
                "BUILDING_ACT",
                "Building Act",
                "LAW",
                "NATIONAL_LAW_OPEN_DATA",
                "2026-02-27",
                "2026-06-04T01:01:00Z",
                "Article changes: 1",
                List.of(new LegalDigestArticleChange(
                        "0025001",
                        "Article 25",
                        "MODIFIED",
                        "old text",
                        "new text",
                        "0018232025082621035",
                        "2026-02-27",
                        "https://www.law.go.kr")));
    }

    private AiModelGateway fakeGateway(String rawText) {
        return request -> new ImmediateAiModelCall(request.modelId(), rawText);
    }

    private static final class DirectLegalDigestAiWorker extends LegalDigestAiWorker {
        private DirectLegalDigestAiWorker() {
            super(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Boolean> submitAndTrackAsync(AiHarnessFlow flow, Duration timeout) {
            var worker = Worker.builder("legal-digest-ai-test").build();
            Engine.builder()
                    .worker(worker)
                    .build()
                    .attach();
            worker.submit(flow.flow());
            for (var i = 0; i < 10; i++) {
                worker.tickOnce();
                if (flow.flow().state().isTerminal()) {
                    return java.util.concurrent.CompletableFuture.completedFuture(true);
                }
            }
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
    }

    private record ImmediateAiModelCall(ModelId modelId, String rawText) implements AiModelCall {
        @Override
        public String callId() {
            return "fake-legal-call";
        }

        @Override
        public AiModelCallStatus poll() {
            return AiModelCallStatus.READY;
        }

        @Override
        public AiModelResponse result() {
            return new AiModelResponse(rawText, modelId, AiModelResponse.ResponseMetadata.empty());
        }

        @Override
        public Throwable error() {
            return null;
        }

        @Override
        public void cancel() {
        }
    }
}
