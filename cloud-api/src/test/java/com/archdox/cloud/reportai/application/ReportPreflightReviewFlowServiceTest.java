package com.archdox.cloud.reportai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.archdox.cloud.engine.application.ArchDoxEngineResultStatus;
import com.archdox.cloud.engine.application.EngineValidationResult;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewFinding;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewRun;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewStatus;
import com.archdox.cloud.reportai.flow.ReportPreflightReviewRequest;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewFindingRepository;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewRunRepository;
import com.archdox.cloud.worker.engine.EngineWorkerActionSubmissionResult;
import com.archdox.cloud.worker.engine.EngineWorkerActionSubmissionService;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ReportPreflightReviewFlowServiceTest {
    private final InspectionReportRepository reportRepository = mock(InspectionReportRepository.class);
    private final ReportPreflightReviewRunRepository runRepository = mock(ReportPreflightReviewRunRepository.class);
    private final ReportPreflightReviewFindingRepository findingRepository = mock(ReportPreflightReviewFindingRepository.class);
    private final ReportPreflightDeterministicValidator deterministicValidator = mock(ReportPreflightDeterministicValidator.class);
    private final ReportPreflightEngineBoundaryService engineBoundaryService = mock(ReportPreflightEngineBoundaryService.class);
    private final EngineWorkerActionSubmissionService workerActionSubmissionService = mock(EngineWorkerActionSubmissionService.class);
    private final ReportPreflightAiHarnessFlowService aiHarnessFlowService = mock(ReportPreflightAiHarnessFlowService.class);
    private final OperationEventService operationEventService = mock(OperationEventService.class);
    private final ReportPreflightFieldValueResolver fieldValueResolver = mock(ReportPreflightFieldValueResolver.class);

    private final ReportPreflightReviewFlowService service = new ReportPreflightReviewFlowService(
            reportRepository,
            runRepository,
            findingRepository,
            deterministicValidator,
            engineBoundaryService,
            workerActionSubmissionService,
            aiHarnessFlowService,
            operationEventService,
            fieldValueResolver);

    @Test
    void carriesOpenFindingWhenSameFieldValueStillExists() {
        var now = OffsetDateTime.parse("2026-06-10T09:00:00+09:00");
        var report = report(now);
        var currentRun = run(200L, now);
        var previousRun = run(199L, now.minusMinutes(10));
        var location = "steps.DAILY_LOG.payload.dailyItems.groups[0].entries[0].supervisionContent";
        var hash = ReportPreflightFieldValueResolver.hashText("column \uACFC \uB97C checked");
        var previousFinding = previousFinding(300L, previousRun.id(), location, hash, now.minusMinutes(10));
        var request = new ReportPreflightReviewRequest(10L, 100L, 200L, 7L);

        when(reportRepository.findByIdAndOfficeId(100L, 10L)).thenReturn(Optional.of(report));
        when(runRepository.findByIdAndOfficeIdAndReportId(200L, 10L, 100L)).thenReturn(Optional.of(currentRun));
        when(deterministicValidator.validate(report)).thenReturn(new ReportPreflightValidationResult(List.of()));
        when(engineBoundaryService.validate(report, 7L)).thenReturn(emptyEngineResult());
        when(workerActionSubmissionService.submitAfterCommit(any(), any())).thenReturn(EngineWorkerActionSubmissionResult.empty());
        when(runRepository.findByOfficeIdAndReportIdOrderByRequestedAtDesc(10L, 100L))
                .thenReturn(List.of(currentRun, previousRun));
        when(findingRepository.findByOfficeIdAndReviewRunIdOrderByIdAsc(10L, 199L))
                .thenReturn(List.of(previousFinding));
        when(fieldValueResolver.resolveHash(100L, location)).thenReturn(Optional.of(hash));

        var result = service.runDeterministicValidation(request);

        var captor = ArgumentCaptor.forClass(ReportPreflightReviewFinding.class);
        org.mockito.Mockito.verify(findingRepository).save(captor.capture());
        assertThat(result.blocksGeneration()).isTrue();
        assertThat(currentRun.status()).isEqualTo(ReportPreflightReviewStatus.NEEDS_ATTENTION);
        assertThat(captor.getValue().source()).isEqualTo("AI");
        assertThat(captor.getValue().attributesJson())
                .containsEntry("carriedOver", "true")
                .containsEntry("carriedOverFromRunId", "199")
                .containsEntry("fieldValueHash", hash);
    }

    @Test
    void storesLegalReferenceSummaryFindingWhenEngineUsesLegalContext() {
        var now = OffsetDateTime.parse("2026-06-10T09:00:00+09:00");
        var report = report(now);
        var currentRun = run(200L, now);
        var request = new ReportPreflightReviewRequest(10L, 100L, 200L, 7L);

        when(reportRepository.findByIdAndOfficeId(100L, 10L)).thenReturn(Optional.of(report));
        when(runRepository.findByIdAndOfficeIdAndReportId(200L, 10L, 100L)).thenReturn(Optional.of(currentRun));
        when(deterministicValidator.validate(report)).thenReturn(new ReportPreflightValidationResult(List.of()));
        when(engineBoundaryService.validate(report, 7L)).thenReturn(engineResultWithLegalReference());
        when(workerActionSubmissionService.submitAfterCommit(any(), any())).thenReturn(EngineWorkerActionSubmissionResult.empty());
        when(runRepository.findByOfficeIdAndReportIdOrderByRequestedAtDesc(10L, 100L))
                .thenReturn(List.of(currentRun));

        var result = service.runDeterministicValidation(request);

        var captor = ArgumentCaptor.forClass(ReportPreflightReviewFinding.class);
        org.mockito.Mockito.verify(findingRepository).save(captor.capture());
        assertThat(result.blocksGeneration()).isFalse();
        assertThat(currentRun.status()).isEqualTo(ReportPreflightReviewStatus.PASSED);
        assertThat(captor.getValue().code()).isEqualTo("LEGAL_EVIDENCE_CONTEXT_USED");
        assertThat(captor.getValue().severity()).isEqualTo("INFO");
        assertThat(captor.getValue().attributesJson())
                .containsEntry("category", "LEGAL_CONTEXT")
                .containsEntry("approvalRequired", "false")
                .containsEntry("legalReferences", "BUILDING_ACT:0025001@v1");
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

    private ReportPreflightReviewRun run(Long id, OffsetDateTime now) {
        var run = new ReportPreflightReviewRun(10L, 100L, 1, 7L, now);
        ReflectionTestUtils.setField(run, "id", id);
        return run;
    }

    private ReportPreflightReviewFinding previousFinding(
            Long id,
            Long reviewRunId,
            String location,
            String hash,
            OffsetDateTime now
    ) {
        var finding = new ReportPreflightReviewFinding(
                10L,
                reviewRunId,
                100L,
                "AI",
                "WORDING",
                "MEDIUM",
                location,
                "Previous run found a wording issue.",
                "same field value",
                Map.of(
                        "category", "WORDING",
                        "approvalRequired", "true",
                        "fieldValueHash", hash,
                        "replacement", "Column checked."),
                now);
        ReflectionTestUtils.setField(finding, "id", id);
        return finding;
    }

    private EngineValidationResult emptyEngineResult() {
        return new EngineValidationResult(
                "engine-run-1",
                ArchDoxEngineResultStatus.PASS,
                true,
                "ok",
                List.of(),
                List.of(),
                List.of(),
                "",
                List.of(),
                "REPORT_PREFLIGHT",
                Map.of());
    }

    private EngineValidationResult engineResultWithLegalReference() {
        var legalReference = new LinkedHashMap<String, Object>();
        legalReference.put("referenceId", "BUILDING_ACT:0025001@v1");
        legalReference.put("actName", "Building Act");
        legalReference.put("articleNo", "25");
        legalReference.put("articleTitle", "Inspection");
        legalReference.put("bindingScope", "CATALOG_ITEM");
        legalReference.put("bindingKey", "STEEL_MEMBER_SYMBOL");
        legalReference.put("relevance", "PRIMARY");
        legalReference.put("catalogCode", "CONSTRUCTION_SUPERVISION");
        legalReference.put("catalogVersion", "1");
        legalReference.put("checklistItemCode", "STEEL_MEMBER_SYMBOL");
        legalReference.put("metadata", Map.of("resolutionSource", "LEGAL_DOMAIN_BINDING"));
        return new EngineValidationResult(
                "engine-run-1",
                ArchDoxEngineResultStatus.PASS,
                true,
                "ok",
                List.of(),
                List.of(Map.copyOf(legalReference)),
                List.of(),
                "",
                List.of(),
                "REPORT_PREFLIGHT",
                Map.of());
    }
}
