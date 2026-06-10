package com.archdox.cloud.reportai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.aipolicy.application.AiHarnessExecutionPlan;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyResolution;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.aipolicy.domain.AiProviderType;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.domain.InspectionReportStep;
import com.archdox.cloud.inspection.domain.PayloadStorageMode;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewFinding;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewRun;
import com.archdox.cloud.reportai.flow.ReportPreflightLegalReviewAiWorker;
import com.archdox.cloud.reportai.flow.ReportPreflightReviewRequest;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewFindingRepository;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import io.github.parkkevinsb.flower.ai.harness.spi.TraceListener;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ReportPreflightLegalReviewHarnessServiceTest {
    private final InspectionReportRepository reportRepository = mock(InspectionReportRepository.class);
    private final InspectionReportStepRepository stepRepository = mock(InspectionReportStepRepository.class);
    private final ReportPreflightReviewRunRepository runRepository = mock(ReportPreflightReviewRunRepository.class);
    private final ReportPreflightReviewFindingRepository findingRepository = mock(ReportPreflightReviewFindingRepository.class);
    private final ReportPhotoEvidenceStatusService photoEvidenceStatusService = mock(ReportPhotoEvidenceStatusService.class);
    private final AiHarnessPolicyExecutionService policyExecutionService = mock(AiHarnessPolicyExecutionService.class);
    private final ReportPreflightLegalReviewAiWorker aiWorker = mock(ReportPreflightLegalReviewAiWorker.class);
    private final AiModelGateway aiModelGateway = mock(AiModelGateway.class);
    private final TraceListener traceListener = mock(TraceListener.class);
    private final OperationEventService operationEventService = mock(OperationEventService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    private final ReportPreflightLegalReviewHarnessService service = new ReportPreflightLegalReviewHarnessService(
            reportRepository,
            stepRepository,
            runRepository,
            findingRepository,
            photoEvidenceStatusService,
            policyExecutionService,
            aiWorker,
            aiModelGateway,
            new ObjectMapper(),
            traceListener,
            operationEventService,
            transactionManager);

    @Test
    void savesInsufficientContextFindingWhenNoLegalReferenceIsAvailable() {
        var now = OffsetDateTime.parse("2026-06-10T09:00:00+09:00");
        var report = report(now);
        var run = run(now);
        var request = new ReportPreflightReviewRequest(10L, 100L, 200L, 7L);
        when(reportRepository.findByIdAndOfficeId(100L, 10L)).thenReturn(Optional.of(report));
        when(runRepository.findByIdAndOfficeIdAndReportId(200L, 10L, 100L)).thenReturn(Optional.of(run));
        when(findingRepository.findByOfficeIdAndReviewRunIdOrderByIdAsc(10L, 200L)).thenReturn(List.of());
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        service.run(request);

        var captor = ArgumentCaptor.forClass(ReportPreflightReviewFinding.class);
        org.mockito.Mockito.verify(findingRepository).deleteByReviewRunIdAndSource(200L, ReportPreflightLegalReviewHarnessService.SOURCE);
        org.mockito.Mockito.verify(findingRepository).save(captor.capture());
        assertThat(captor.getValue().source()).isEqualTo("LEGAL_REVIEW");
        assertThat(captor.getValue().code()).isEqualTo("LEGAL_REVIEW_INSUFFICIENT_CONTEXT");
        assertThat(captor.getValue().attributesJson())
                .containsEntry("category", "LEGAL_REVIEW")
                .containsEntry("approvalRequired", "true")
                .containsEntry("legalReviewStatus", "INSUFFICIENT_CONTEXT");
    }

    @Test
    void submitsAiFlowWhenLegalReferencesAreAvailable() {
        var now = OffsetDateTime.parse("2026-06-10T09:00:00+09:00");
        var report = report(now);
        var run = run(now);
        var request = new ReportPreflightReviewRequest(10L, 100L, 200L, 7L);
        when(reportRepository.findByIdAndOfficeId(100L, 10L)).thenReturn(Optional.of(report));
        when(runRepository.findByIdAndOfficeIdAndReportId(200L, 10L, 100L)).thenReturn(Optional.of(run));
        when(findingRepository.findByOfficeIdAndReviewRunIdOrderByIdAsc(10L, 200L))
                .thenReturn(List.of(legalContextFinding(now)));
        when(photoEvidenceStatusService.evaluate(report)).thenReturn(emptyPhotoEvidenceStatus());
        when(stepRepository.findByReportIdOrderById(100L)).thenReturn(List.of(new InspectionReportStep(
                report,
                "DAILY_LOG",
                PayloadStorageMode.CLOUD_ENCRYPTED,
                Map.of("supervisionContent", "철근 배근 상태를 확인했습니다."),
                7L,
                now)));
        when(policyExecutionService.resolve(AiHarnessPolicyKey.SOURCE_BACKED_LEGAL_REVIEW))
                .thenReturn(AiHarnessPolicyResolution.runnable(new AiHarnessExecutionPlan(
                        AiHarnessPolicyKey.SOURCE_BACKED_LEGAL_REVIEW,
                        provider(now),
                        new ModelId("openai-main", "gpt-4.1-mini"),
                        2,
                        Duration.ofSeconds(90))));
        when(aiWorker.submitAndAwait(any(), any())).thenReturn(true);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        service.run(request);

        verify(aiWorker).submitAndAwait(any(), any());
    }

    private InspectionReport report(OffsetDateTime now) {
        var report = new InspectionReport(
                10L,
                20L,
                30L,
                "R-001",
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "Daily supervision log",
                40L,
                7L,
                now);
        ReflectionTestUtils.setField(report, "id", 100L);
        return report;
    }

    private ReportPreflightReviewRun run(OffsetDateTime now) {
        var run = new ReportPreflightReviewRun(10L, 100L, 1, 7L, now);
        ReflectionTestUtils.setField(run, "id", 200L);
        return run;
    }

    private ReportPreflightReviewFinding legalContextFinding(OffsetDateTime now) {
        return new ReportPreflightReviewFinding(
                10L,
                200L,
                100L,
                "DETERMINISTIC",
                "LEGAL_EVIDENCE_CONTEXT_USED",
                "INFO",
                "LEGAL_CONTEXT",
                "법령 근거를 사용해 생성 전 검토를 수행했습니다.",
                "legalReferences=BUILDING_ACT:0025001@0018232025082621035",
                Map.of(
                        "legalReferences", "BUILDING_ACT:0025001@0018232025082621035",
                        "legalReferenceDetails",
                        "BUILDING_ACT:0025001@0018232025082621035\t건축법 25 건축물의 공사감리\tLEGAL_DOMAIN_BINDING\tREPORT_TYPE\tCONSTRUCTION_DAILY_SUPERVISION_LOG:BUILDING_ACT_SUPERVISION\tPRIMARY\t\t\t"),
                now);
    }

    private ReportPhotoEvidenceStatus emptyPhotoEvidenceStatus() {
        return new ReportPhotoEvidenceStatus(
                true,
                0,
                0,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of());
    }

    private AiProviderCredential provider(OffsetDateTime now) {
        var provider = new AiProviderCredential(
                "openai-main",
                "OpenAI Main",
                AiProviderType.OPENAI,
                null,
                "gpt-4.1-mini",
                "encrypted",
                "fingerprint",
                1L,
                now);
        ReflectionTestUtils.setField(provider, "id", 4L);
        provider.publish(now);
        return provider;
    }
}
