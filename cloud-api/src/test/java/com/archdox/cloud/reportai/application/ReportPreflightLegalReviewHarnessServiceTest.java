package com.archdox.cloud.reportai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.aipolicy.application.AiHarnessExecutionPlan;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyResolution;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.aipolicy.domain.AiProviderType;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.domain.InspectionReportStep;
import com.archdox.cloud.inspection.domain.PayloadStorageMode;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.reportai.domain.ReportPreflightFindingResolutionStatus;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewFinding;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewRun;
import com.archdox.cloud.reportai.flow.ReportPreflightLegalReviewAiWorker;
import com.archdox.cloud.reportai.flow.ReportPreflightReviewRequest;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewFindingRepository;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewRunRepository;
import com.archdox.legalai.SourceBackedLegalReviewIssue;
import com.archdox.legalai.SourceBackedLegalReviewIssueCategory;
import com.archdox.legalai.SourceBackedLegalReviewIssueSeverity;
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

        var submission = service.submit(request);

        var captor = ArgumentCaptor.forClass(ReportPreflightReviewFinding.class);
        org.mockito.Mockito.verify(findingRepository).deleteByReviewRunIdAndSource(200L, ReportPreflightLegalReviewHarnessService.SOURCE);
        org.mockito.Mockito.verify(findingRepository).save(captor.capture());
        assertThat(submission.submitted()).isFalse();
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
                Map.of("dailyItems", Map.of("groups", List.of(Map.of(
                        "tradeCode", "REINFORCED_CONCRETE",
                        "processCode", "REBAR_ASSEMBLY",
                        "entries", List.of(Map.of(
                                "inspectionItemCode", "RC_REBAR_CONFIRMATION",
                                "inspectionItemName", "철근배근의 확인사항",
                                "checklistRows", List.of(Map.of(
                                        "code", "RC_REBAR_COUNT_DIAMETER_PITCH",
                                        "label", "개수, 철근지름, 피치 확인",
                                        "result", "COMPLIANT",
                                        "referenceNote", "철근 배근 상태를 확인했습니다.")))))))),
                7L,
                now)));
        when(policyExecutionService.resolve(AiHarnessPolicyKey.SOURCE_BACKED_LEGAL_REVIEW))
                .thenReturn(AiHarnessPolicyResolution.runnable(new AiHarnessExecutionPlan(
                        AiHarnessPolicyKey.SOURCE_BACKED_LEGAL_REVIEW,
                        provider(now),
                        new ModelId("openai-main", "gpt-4.1-mini"),
                        2,
                        Duration.ofSeconds(90))));
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        var submission = service.submit(request);

        assertThat(submission.submitted()).isTrue();
        assertThat(submission.flow()).isNotNull();
        verify(aiWorker).submit(any());
    }

    @Test
    void skippedLegalReviewFindingIsResolvedBecauseItIsDisplayOnly() {
        var now = OffsetDateTime.parse("2026-06-10T09:00:00+09:00");
        var report = report(now);
        var run = run(now);
        var request = new ReportPreflightReviewRequest(10L, 100L, 200L, 7L);
        when(reportRepository.findByIdAndOfficeId(100L, 10L)).thenReturn(Optional.of(report));
        when(runRepository.findByIdAndOfficeIdAndReportId(200L, 10L, 100L)).thenReturn(Optional.of(run));
        when(findingRepository.findByOfficeIdAndReviewRunIdOrderByIdAsc(10L, 200L))
                .thenReturn(List.of(legalContextFinding(now)));
        when(policyExecutionService.resolve(AiHarnessPolicyKey.SOURCE_BACKED_LEGAL_REVIEW))
                .thenReturn(AiHarnessPolicyResolution.unavailable(
                        AiHarnessPolicyKey.SOURCE_BACKED_LEGAL_REVIEW,
                        "POLICY_DISABLED"));
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        var submission = service.submit(request);

        var captor = ArgumentCaptor.forClass(ReportPreflightReviewFinding.class);
        org.mockito.Mockito.verify(findingRepository).save(captor.capture());
        assertThat(submission.submitted()).isFalse();
        assertThat(captor.getValue().code()).isEqualTo("LEGAL_REVIEW_SKIPPED");
        assertThat(captor.getValue().resolutionStatus()).isEqualTo(ReportPreflightFindingResolutionStatus.RESOLVED);
        assertThat(captor.getValue().resolutionNote()).isEqualTo("DISPLAY_ONLY_LEGAL_REVIEW_SUMMARY");
    }

    @Test
    void budgetExhaustionSkipsLegalReviewInsteadOfSubmittingOrFailingPreflight() {
        var now = OffsetDateTime.parse("2026-06-10T09:00:00+09:00");
        var report = report(now);
        var run = run(now);
        var request = new ReportPreflightReviewRequest(10L, 100L, 200L, 7L);
        var plan = new AiHarnessExecutionPlan(
                AiHarnessPolicyKey.SOURCE_BACKED_LEGAL_REVIEW,
                provider(now),
                new ModelId("openai-main", "gpt-4.1-mini"),
                2,
                Duration.ofSeconds(90));
        when(reportRepository.findByIdAndOfficeId(100L, 10L)).thenReturn(Optional.of(report));
        when(runRepository.findByIdAndOfficeIdAndReportId(200L, 10L, 100L)).thenReturn(Optional.of(run));
        when(findingRepository.findByOfficeIdAndReviewRunIdOrderByIdAsc(10L, 200L))
                .thenReturn(List.of(legalContextFinding(now)));
        when(policyExecutionService.resolve(AiHarnessPolicyKey.SOURCE_BACKED_LEGAL_REVIEW))
                .thenReturn(AiHarnessPolicyResolution.runnable(plan));
        org.mockito.Mockito.doThrow(new BadRequestException(
                        "AI_HARNESS_MONTHLY_TOKEN_LIMIT_EXCEEDED",
                        "errors.aiHarness.monthlyTokenLimitExceeded",
                        "Monthly AI token limit exceeded for this harness"))
                .when(policyExecutionService).requireWithinBudget(plan);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        var submission = service.submit(request);

        var captor = ArgumentCaptor.forClass(ReportPreflightReviewFinding.class);
        org.mockito.Mockito.verify(findingRepository).save(captor.capture());
        assertThat(submission.submitted()).isFalse();
        assertThat(captor.getValue().code()).isEqualTo("LEGAL_REVIEW_SKIPPED");
        assertThat(captor.getValue().resolutionStatus()).isEqualTo(ReportPreflightFindingResolutionStatus.RESOLVED);
        assertThat(captor.getValue().attributesJson())
                .containsEntry("legalReviewStatus", "SKIPPED")
                .containsEntry("skipReason", "Monthly AI token limit exceeded for this harness");
        verify(aiWorker, never()).submit(any());
    }

    @Test
    void legalReferencesArePrioritizedAndAnnotatedForSourceBackedReview() {
        var now = OffsetDateTime.parse("2026-06-10T09:00:00+09:00");

        @SuppressWarnings("unchecked")
        var references = (List<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
                service,
                "legalReferences",
                List.of(
                        legalSearchCandidateFinding(now),
                        legalContextFinding(now)));

        assertThat(references).isNotNull();
        assertThat(references).hasSize(2);
        assertThat(references.get(0))
                .containsEntry("referenceId", "BUILDING_ACT:0025001@0018232025082621035")
                .containsEntry("anchorRole", "REPORT_TYPE_ANCHOR")
                .containsEntry("sourceFindingCode", "LEGAL_EVIDENCE_CONTEXT_USED");
        assertThat((Integer) references.get(0).get("referencePriorityScore"))
                .isGreaterThan((Integer) references.get(1).get("referencePriorityScore"));
        assertThat(references.get(1))
                .containsEntry("referenceId", "CONSTRUCTION_SUPERVISION_DETAILED_STANDARD:BODY@v1")
                .containsEntry("anchorRole", "SEARCH_CANDIDATE");
    }

    @Test
    void candidateOnlyCoverageIsNotPassEligible() {
        var now = OffsetDateTime.parse("2026-06-10T09:00:00+09:00");

        @SuppressWarnings("unchecked")
        var references = (List<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
                service,
                "legalReferences",
                List.of(legalSearchCandidateFinding(now)));
        var coverage = ReflectionTestUtils.invokeMethod(service, "legalReferenceCoverage", references);

        @SuppressWarnings("unchecked")
        var coverageMap = (Map<String, Object>) ReflectionTestUtils.invokeMethod(coverage, "toMap");
        assertThat(coverageMap)
                .containsEntry("passEligibleForPass", false)
                .containsEntry("legalReferenceGrade", "C")
                .containsEntry("reviewStrength", "LOW")
                .containsEntry("candidateCount", 1)
                .containsEntry("businessItemAnchorCount", 0);
        assertThat(passEligibility(coverageMap))
                .containsEntry("legalEligible", false)
                .containsEntry("finalEligible", false);
        assertThat(passBlockerCodes(coverageMap))
                .contains(
                        "PASS_BLOCKED_SEARCH_CANDIDATE_ONLY",
                        "PASS_BLOCKED_NO_BUSINESS_ITEM_ANCHOR");
    }

    @Test
    void businessItemDomainBindingCoverageIsHighStrength() {
        var now = OffsetDateTime.parse("2026-06-10T09:00:00+09:00");

        @SuppressWarnings("unchecked")
        var references = (List<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
                service,
                "legalReferences",
                List.of(businessItemLegalContextFinding(now)));
        var coverage = ReflectionTestUtils.invokeMethod(service, "legalReferenceCoverage", references);

        @SuppressWarnings("unchecked")
        var coverageMap = (Map<String, Object>) ReflectionTestUtils.invokeMethod(coverage, "toMap");
        assertThat(coverageMap)
                .containsEntry("passEligibleForPass", true)
                .containsEntry("legalReferenceGrade", "A")
                .containsEntry("reviewStrength", "HIGH")
                .containsEntry("primaryCount", 1)
                .containsEntry("businessItemAnchorCount", 1);
        assertThat(passEligibility(coverageMap))
                .containsEntry("legalEligible", true)
                .containsEntry("evidenceEligible", true)
                .containsEntry("technicalCriteriaEligible", true)
                .containsEntry("applicabilityEligible", true)
                .containsEntry("finalEligible", true);
        assertThat(passBlockerCodes(coverageMap)).isEmpty();
    }

    @Test
    void reportTypeAnchorOnlyBlocksFinalPass() {
        var now = OffsetDateTime.parse("2026-06-10T09:00:00+09:00");

        @SuppressWarnings("unchecked")
        var references = (List<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
                service,
                "legalReferences",
                List.of(legalContextFinding(now)));
        var coverage = ReflectionTestUtils.invokeMethod(service, "legalReferenceCoverage", references);

        @SuppressWarnings("unchecked")
        var coverageMap = (Map<String, Object>) ReflectionTestUtils.invokeMethod(coverage, "toMap");
        assertThat(coverageMap)
                .containsEntry("passEligibleForPass", false)
                .containsEntry("legalReferenceGrade", "B")
                .containsEntry("reviewStrength", "MEDIUM")
                .containsEntry("reportTypeAnchorCount", 1)
                .containsEntry("businessItemAnchorCount", 0);
        assertThat(passEligibility(coverageMap))
                .containsEntry("legalEligible", true)
                .containsEntry("applicabilityEligible", false)
                .containsEntry("finalEligible", false);
        assertThat(passBlockerCodes(coverageMap))
                .contains(
                        "PASS_BLOCKED_REPORT_TYPE_ANCHOR_ONLY",
                        "PASS_BLOCKED_NO_BUSINESS_ITEM_ANCHOR");
    }

    @Test
    void missingEvidenceBlocksFinalPass() {
        var now = OffsetDateTime.parse("2026-06-10T09:00:00+09:00");

        @SuppressWarnings("unchecked")
        var references = (List<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
                service,
                "legalReferences",
                List.of(businessItemLegalContextFinding(now)));
        var evidenceChecklist = Map.<String, Object>of(
                "reportType", "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "dailyLogEntryCount", 1,
                "dailyLogEntriesWithSupervisionContent", 1,
                "dailyLogEntriesWithChecklistItemCode", 1,
                "dailyLogEntriesWithPhotoIds", 0,
                "allDailyLogPhotoRefsResolved", true,
                "generationBlockingPhotoIssue", false);
        var coverage = ReflectionTestUtils.invokeMethod(
                service,
                "legalReferenceCoverage",
                references,
                evidenceChecklist);

        @SuppressWarnings("unchecked")
        var coverageMap = (Map<String, Object>) ReflectionTestUtils.invokeMethod(coverage, "toMap");
        assertThat(coverageMap)
                .containsEntry("passEligibleForPass", false)
                .containsEntry("legalReferenceGrade", "A")
                .containsEntry("reviewStrength", "LOW");
        assertThat(passEligibility(coverageMap))
                .containsEntry("legalEligible", true)
                .containsEntry("evidenceEligible", false)
                .containsEntry("technicalCriteriaEligible", true)
                .containsEntry("applicabilityEligible", true)
                .containsEntry("finalEligible", false);
        assertThat(passBlockerCodes(coverageMap))
                .contains("PASS_BLOCKED_MISSING_PHOTO_EVIDENCE");
    }

    @Test
    void technicalCriteriaEvidenceGapLimitsScopeWithoutBlockingRecordReviewPass() {
        var now = OffsetDateTime.parse("2026-06-10T09:00:00+09:00");

        @SuppressWarnings("unchecked")
        var references = (List<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
                service,
                "legalReferences",
                List.of(businessItemLegalContextFinding(now)));
        var evidenceChecklist = Map.<String, Object>of(
                "reportType", "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "dailyLogEntryCount", 1,
                "dailyLogEntriesWithSupervisionContent", 1,
                "dailyLogEntriesWithChecklistItemCode", 1,
                "dailyLogEntriesWithPhotoIds", 1,
                "allDailyLogPhotoRefsResolved", true,
                "generationBlockingPhotoIssue", false,
                "technicalCriteriaReviewRequired", true,
                "dailyLogEntriesRequiringTechnicalCriteria", 1,
                "dailyLogEntriesWithTechnicalCriteriaEvidence", 0);
        var coverage = ReflectionTestUtils.invokeMethod(
                service,
                "legalReferenceCoverage",
                references,
                evidenceChecklist);

        @SuppressWarnings("unchecked")
        var coverageMap = (Map<String, Object>) ReflectionTestUtils.invokeMethod(coverage, "toMap");
        assertThat(coverageMap)
                .containsEntry("passEligibleForPass", true)
                .containsEntry("legalReferenceGrade", "A")
                .containsEntry("reviewStrength", "MEDIUM");
        assertThat(passEligibility(coverageMap))
                .containsEntry("legalEligible", true)
                .containsEntry("evidenceEligible", true)
                .containsEntry("technicalCriteriaEligible", false)
                .containsEntry("applicabilityEligible", true)
                .containsEntry("finalEligible", true);
        assertThat(passBlockerCodes(coverageMap)).isEmpty();
        assertThat((List<?>) coverageMap.get("limitations"))
                .map(String::valueOf)
                .contains("성능·규격 등 실질 기술기준 적합성은 설계도서, 시방서, 시험성적서, 승인서 등 별도 근거 문서가 연결되지 않아 검토 범위에서 제외했습니다.");
    }

    @Test
    void documentCompletionReplacementWritesDailyLogConfirmationAndAttachmentProse() {
        var windowReplacement = (String) ReflectionTestUtils.invokeMethod(
                service,
                "documentCompletionReplacement",
                Map.of("tradeName", "창호공사", "processName", "창호 자재성능"),
                Map.of(
                        "inspectionItemName", "창호 자재성능",
                        "inspectionItemCode", "WINDOW_MATERIAL_PERFORMANCE",
                        "checklistRows", List.of(Map.of(
                                "code", "WINDOW_MATERIAL_PERFORMANCE",
                                "label", "창호 자재 성능 확인",
                                "result", "COMPLIANT",
                                "referenceNote", "창호 자재 성능 확인시 이상 없음"))));
        var insulationReplacement = (String) ReflectionTestUtils.invokeMethod(
                service,
                "documentCompletionReplacement",
                Map.of("tradeName", "단열공사", "processName", "단열재 자재성능"),
                Map.of(
                        "inspectionItemName", "단열재 자재성능",
                        "inspectionItemCode", "INSULATION_MATERIAL",
                        "checklistRows", List.of(Map.of(
                                "code", "INSULATION_MATERIAL",
                                "label", "단열재 자재성능 확인",
                                "result", "COMPLIANT",
                                "referenceNote", "단열재 자재성능 확인"))));

        assertThat(windowReplacement)
                .contains("창호 자재의 단열·기밀·수밀·내풍압")
                .contains("시방서·시험성적서·자재승인서")
                .contains("확인하고 첨부하였음을 기록합니다");
        assertThat(insulationReplacement)
                .contains("단열재의 규격, 두께 및 성능 항목")
                .contains("확인하고 첨부하였음을 기록합니다");
    }

    @Test
    void technicalCriteriaScopeIssueDoesNotRequireGenerationBlockingResolution() {
        var issue = new SourceBackedLegalReviewIssue(
                "TECHNICAL_CRITERIA_MISSING",
                SourceBackedLegalReviewIssueCategory.EVIDENCE,
                SourceBackedLegalReviewIssueSeverity.MEDIUM,
                "DAILY_LOG",
                "창호 자재성능에 대한 실질 기술기준 증거가 제출되지 않아 기술기준 적합성 판단이 불가능합니다.",
                "감리일지에는 기록과 사진이 있으나 성능·규격 근거 문서는 연결되지 않았습니다.",
                "일반 감리일지 생성에서는 범위 제한으로 표시합니다.",
                List.of("BUILDING_ACT:0025001@0018232025082621035"),
                "DAILY_LOG.groups[0].entries[0].checklistRows");

        var approvalRequired = ReflectionTestUtils.invokeMethod(service, "legalIssueApprovalRequired", issue);

        assertThat(approvalRequired).isEqualTo(false);
    }

    @Test
    void nonBlockingLegalReviewFindingIsAutoResolvedAsDisplayOnlyNotice() {
        var finding = new ReportPreflightReviewFinding(
                10L,
                200L,
                100L,
                "LEGAL_REVIEW",
                "TECHNICAL_CRITERIA_EVIDENCE_MISSING",
                "MEDIUM",
                "DAILY_LOG",
                "기술기준 적합성 증빙 자료가 연결되지 않았습니다.",
                "scope limitation",
                Map.of(
                        "category", "EVIDENCE",
                        "approvalRequired", "false",
                        "legalReviewStatus", "WARN"),
                OffsetDateTime.parse("2026-06-10T09:00:00+09:00"));

        ReportPreflightFindingClassifier.autoResolveOnCreate(
                finding,
                7L,
                OffsetDateTime.parse("2026-06-10T09:01:00+09:00"));

        assertThat(finding.resolutionStatus()).isEqualTo(ReportPreflightFindingResolutionStatus.RESOLVED);
        assertThat(finding.resolutionNote()).isEqualTo("DISPLAY_ONLY_LEGAL_REVIEW_SUMMARY");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> passEligibility(Map<String, Object> coverageMap) {
        return (Map<String, Object>) coverageMap.get("passEligibility");
    }

    @SuppressWarnings("unchecked")
    private List<String> passBlockerCodes(Map<String, Object> coverageMap) {
        return ((List<Map<String, Object>>) coverageMap.get("passBlockers"))
                .stream()
                .map(blocker -> String.valueOf(blocker.get("code")))
                .toList();
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

    private ReportPreflightReviewFinding legalSearchCandidateFinding(OffsetDateTime now) {
        return new ReportPreflightReviewFinding(
                10L,
                200L,
                100L,
                "DETERMINISTIC",
                "LEGAL_SEARCH_CANDIDATE_USED",
                "INFO",
                "LEGAL_CONTEXT",
                "법령 검색 후보를 사용했습니다.",
                "legalReferences=CONSTRUCTION_SUPERVISION_DETAILED_STANDARD:BODY@v1",
                Map.of(
                        "legalReferences", "CONSTRUCTION_SUPERVISION_DETAILED_STANDARD:BODY@v1",
                        "legalReferenceDetails",
                        "CONSTRUCTION_SUPERVISION_DETAILED_STANDARD:BODY@v1\t건축공사 감리세부기준 본문\tLEGAL_SEARCH\tLEGAL_CORPUS_SEARCH\t\tCANDIDATE\t\t\t"),
                now);
    }

    private ReportPreflightReviewFinding businessItemLegalContextFinding(OffsetDateTime now) {
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
                        "BUILDING_ACT:0025001@0018232025082621035\t건축법 25 건축물의 공사감리\tLEGAL_DOMAIN_BINDING\tCATALOG_ITEM\tSTEEL_MEMBER_SYMBOL\tPRIMARY\tCONSTRUCTION_SUPERVISION_CHECKLIST\tv1\tSTEEL_MEMBER_SYMBOL"),
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
