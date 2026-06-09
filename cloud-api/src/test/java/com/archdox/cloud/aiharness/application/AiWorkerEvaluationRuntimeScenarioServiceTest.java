package com.archdox.cloud.aiharness.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyResolution;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.legal.domain.LegalChangeDigest;
import com.archdox.cloud.legal.domain.LegalChangeDigestSource;
import com.archdox.cloud.legal.domain.LegalChangeDigestStatus;
import com.archdox.cloud.legal.infra.LegalChangeDigestRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.cloud.worker.ArchDoxWorkerServiceWorker;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import com.archdox.worker.flow.ArchDoxWorkerExecutionFlowFactory;
import com.archdox.worker.flow.ArchDoxWorkerExecutionHandle;
import com.archdox.worker.flow.ArchDoxWorkerExecutionSession;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class AiWorkerEvaluationRuntimeScenarioServiceTest {
    private final PlatformAdminService platformAdminService = mock(PlatformAdminService.class);
    private final AiWorkerEvaluationReadService readService = new AiWorkerEvaluationReadService(platformAdminService);
    private final LegalChangeDigestRepository digestRepository = mock(LegalChangeDigestRepository.class);
    private final ArchDoxWorkerExecutionFlowFactory workerFlowFactory = mock(ArchDoxWorkerExecutionFlowFactory.class);
    private final ArchDoxWorkerServiceWorker workerServiceWorker = mock(ArchDoxWorkerServiceWorker.class);
    private final AiHarnessPolicyExecutionService aiHarnessPolicyExecutionService = mock(AiHarnessPolicyExecutionService.class);
    private final AiWorkerEvaluationTokenControlService tokenControlService = mock(AiWorkerEvaluationTokenControlService.class);
    private final AiWorkerEvaluationRuntimeScenarioService service = new AiWorkerEvaluationRuntimeScenarioService(
            readService,
            digestRepository,
            workerFlowFactory,
            workerServiceWorker,
            aiHarnessPolicyExecutionService,
            tokenControlService);

    @Test
    void runtimeScenarioWarnsWhenNoNonFakeLegalDigestExists() {
        var principal = new UserPrincipal(7L, "platform@test.co.kr");
        when(digestRepository.findAllExcludingSourceCode(any(), any(Pageable.class))).thenReturn(List.of());
        when(tokenControlService.tokenControlGroups()).thenReturn(List.of());
        when(tokenControlService.tokenControlSignals(List.of())).thenReturn(List.of());

        var summary = service.runtimeScenario(principal);

        assertThat(summary.evaluationMode()).isEqualTo("RUNTIME_WORKER_SCENARIO");
        assertThat(summary.groups()).hasSize(6);
        assertThat(summary.groups().getLast().groupKey()).isEqualTo("RUNTIME_WORKER_SCENARIO");
        assertThat(summary.groups().getLast().warningCases()).isEqualTo(1);
        assertThat(summary.signals()).anySatisfy(signal -> {
            assertThat(signal.signalKey()).isEqualTo("RUNTIME_WORKER_SCENARIO");
            assertThat(signal.status()).isEqualTo("WARN");
        });
        verify(workerServiceWorker, never()).submitAndAwait(any(), any());
    }

    @Test
    void runtimeScenarioRunsLegalDigestWorkerDryRunThroughWorkerFlow() {
        var principal = new UserPrincipal(7L, "platform@test.co.kr");
        var digest = digest(4L, 8L);
        when(digestRepository.findAllExcludingSourceCode(any(), any(Pageable.class))).thenReturn(List.of(digest));
        when(aiHarnessPolicyExecutionService.resolve(AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT))
                .thenReturn(AiHarnessPolicyResolution.unavailable(
                        AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT,
                        "PROVIDER_NOT_ASSIGNED"));
        when(workerFlowFactory.createHandle(any(ArchDoxWorkerRequest.class), any(ArchDoxWorkerAction.class)))
                .thenAnswer(invocation -> {
                    var request = invocation.getArgument(0, ArchDoxWorkerRequest.class);
                    var action = invocation.getArgument(1, ArchDoxWorkerAction.class);
                    var session = new ArchDoxWorkerExecutionSession(request, action);
                    session.result(ArchDoxWorkerActionResult.succeeded(Map.of(
                            "dryRun", true,
                            "publicationApplied", false,
                            "corpusMutated", false,
                            "digestMutated", false,
                            "aiHarnessRunId", "harness-run-1",
                            "digestDraftStatus", "NEEDS_HUMAN_REVIEW",
                            "keyArticles", List.of("BUILDING_ACT:25"))));
                    return new ArchDoxWorkerExecutionHandle(noopFlow(), session);
                });
        when(workerServiceWorker.submitAndAwait(any(), any())).thenReturn(true);
        when(tokenControlService.tokenControlGroups()).thenReturn(List.of());
        when(tokenControlService.tokenControlSignals(List.of())).thenReturn(List.of());

        var summary = service.runtimeScenario(principal);

        assertThat(summary.evaluationMode()).isEqualTo("RUNTIME_WORKER_SCENARIO");
        assertThat(summary.groups().getLast().groupKey()).isEqualTo("RUNTIME_WORKER_SCENARIO");
        assertThat(summary.groups().getLast().passedCases()).isEqualTo(4);
        assertThat(summary.groups().getLast().warningCases()).isZero();
        assertThat(summary.signals()).anySatisfy(signal -> {
            assertThat(signal.signalKey()).isEqualTo("RUNTIME_WORKER_SCENARIO");
            assertThat(signal.status()).isEqualTo("PASS");
        });
        verify(workerFlowFactory).createHandle(any(ArchDoxWorkerRequest.class), any(ArchDoxWorkerAction.class));
        verify(workerServiceWorker).submitAndAwait(any(), any());
    }

    private LegalChangeDigest digest(Long id, Long changeSetId) {
        var now = OffsetDateTime.parse("2026-06-09T00:00:00+09:00");
        var digest = new LegalChangeDigest(
                changeSetId,
                LegalChangeDigestStatus.PUBLISHED,
                LegalChangeDigestSource.DETERMINISTIC,
                "Building Act changed",
                "Article changes detected.",
                "Review templates.",
                List.of("CONSTRUCTION_SUPERVISION_REPORT"),
                List.of("CONSTRUCTION_SUPERVISION_LEGAL_CONTEXT"),
                null,
                LocalDate.of(2026, 2, 27),
                now,
                now,
                now);
        ReflectionTestUtils.setField(digest, "id", id);
        return digest;
    }

    private Flow noopFlow() {
        return Flow.builder("test", "test")
                .step("noop", new Step() {
                    @Override
                    protected StepResult onTick(StepContext ctx) {
                        return StepResult.done();
                    }
                })
                .build();
    }
}
