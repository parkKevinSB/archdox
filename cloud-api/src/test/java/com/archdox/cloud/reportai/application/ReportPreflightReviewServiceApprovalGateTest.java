package com.archdox.cloud.reportai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.documentai.application.DocumentAiReviewProperties;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.office.application.OfficePermissionService;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.reportai.domain.ReportPreflightFindingResolutionStatus;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewFinding;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewStatus;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewRun;
import com.archdox.cloud.reportai.dto.ResolveReportPreflightFindingRequest;
import com.archdox.cloud.reportai.flow.ReportPreflightReviewFlowFactory;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewFindingRepository;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewRunRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReportPreflightReviewServiceApprovalGateTest {
    private final InspectionReportRepository reportRepository = mock(InspectionReportRepository.class);
    private final OfficePermissionService permissionService = mock(OfficePermissionService.class);
    private final ReportPreflightReviewRunRepository runRepository = mock(ReportPreflightReviewRunRepository.class);
    private final ReportPreflightReviewFindingRepository findingRepository = mock(ReportPreflightReviewFindingRepository.class);
    private final OperationEventService operationEventService = mock(OperationEventService.class);
    private final ReportPreflightReviewService service = new ReportPreflightReviewService(
            reportRepository,
            permissionService,
            runRepository,
            findingRepository,
            mock(ReportPreflightReviewFlowFactory.class),
            mock(DocumentAiReviewProperties.class),
            operationEventService);

    @AfterEach
    void clearOfficeContext() {
        OfficeContext.clear();
    }

    @Test
    void acceptedAiDraftFindingLetsPreflightRunPass() {
        OfficeContext.set(10L);
        var now = OffsetDateTime.parse("2026-06-10T01:00:00+09:00");
        var report = report(now);
        var run = run(now);
        var finding = aiFinding(300L, now);
        arrange(report, run, finding);
        when(findingRepository.findByOfficeIdAndReviewRunIdOrderByIdAsc(10L, 200L)).thenReturn(List.of(finding));

        var response = service.resolveFinding(
                100L,
                200L,
                300L,
                new ResolveReportPreflightFindingRequest("ACCEPTED", "AI dry-run reviewed by human."),
                new UserPrincipal(7L, "writer@test.co.kr"));

        assertThat(response.resolutionStatus()).isEqualTo(ReportPreflightFindingResolutionStatus.ACCEPTED.name());
        assertThat(run.status()).isEqualTo(ReportPreflightReviewStatus.PASSED);
        assertThat(run.terminalReason()).isEqualTo("PREFLIGHT_FINDINGS_RESOLVED");
        verify(permissionService).requireReportWriter(7L, 10L, 20L, 100L);
        verify(operationEventService).record(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void resolvingOneOfMultipleAttentionFindingsKeepsPreflightRunBlocked() {
        OfficeContext.set(10L);
        var now = OffsetDateTime.parse("2026-06-10T01:00:00+09:00");
        var report = report(now);
        var run = run(now);
        var first = aiFinding(300L, now);
        var second = aiFinding(301L, now);
        arrange(report, run, first);
        when(findingRepository.findByOfficeIdAndReviewRunIdOrderByIdAsc(10L, 200L)).thenReturn(List.of(first, second));

        service.resolveFinding(
                100L,
                200L,
                300L,
                new ResolveReportPreflightFindingRequest("RESOLVED", "First item reviewed."),
                new UserPrincipal(7L, "writer@test.co.kr"));

        assertThat(first.resolutionStatus()).isEqualTo(ReportPreflightFindingResolutionStatus.RESOLVED);
        assertThat(second.resolutionStatus()).isEqualTo(ReportPreflightFindingResolutionStatus.OPEN);
        assertThat(run.status()).isEqualTo(ReportPreflightReviewStatus.NEEDS_ATTENTION);
    }

    @Test
    void resolvingLastOpenAttentionFindingLetsPreflightRunPass() {
        OfficeContext.set(10L);
        var now = OffsetDateTime.parse("2026-06-10T01:00:00+09:00");
        var report = report(now);
        var run = run(now);
        var first = aiFinding(300L, now);
        var second = aiFinding(301L, now);
        first.resolve(ReportPreflightFindingResolutionStatus.ACCEPTED, "Already reviewed.", 7L, now.minusMinutes(1));
        arrange(report, run, second);
        when(findingRepository.findByOfficeIdAndReviewRunIdOrderByIdAsc(10L, 200L)).thenReturn(List.of(first, second));

        service.resolveFinding(
                100L,
                200L,
                301L,
                new ResolveReportPreflightFindingRequest("RESOLVED", "Second item reviewed."),
                new UserPrincipal(7L, "writer@test.co.kr"));

        assertThat(run.status()).isEqualTo(ReportPreflightReviewStatus.PASSED);
        assertThat(run.terminalReason()).isEqualTo("PREFLIGHT_FINDINGS_RESOLVED");
    }

    private void arrange(
            InspectionReport report,
            ReportPreflightReviewRun run,
            ReportPreflightReviewFinding finding
    ) {
        when(reportRepository.findByIdAndOfficeId(100L, 10L)).thenReturn(Optional.of(report));
        when(runRepository.findByIdAndOfficeIdAndReportId(200L, 10L, 100L)).thenReturn(Optional.of(run));
        when(findingRepository.findByIdAndOfficeIdAndReviewRunIdAndReportId(finding.id(), 10L, 200L, 100L))
                .thenReturn(Optional.of(finding));
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
        run.markNeedsAttention("AI_PREFLIGHT_NEEDS_HUMAN_REVIEW", now);
        return run;
    }

    private ReportPreflightReviewFinding aiFinding(Long id, OffsetDateTime now) {
        var finding = new ReportPreflightReviewFinding(
                10L,
                200L,
                100L,
                "AI",
                "LEGAL_REVIEW_DRAFT",
                "MEDIUM",
                "DAILY_LOG",
                "AI legal review draft requires human approval.",
                "source-backed legal references were supplied",
                Map.of(
                        "approvalRequired", "true",
                        "draftOnly", "true"),
                now);
        ReflectionTestUtils.setField(finding, "id", id);
        return finding;
    }
}
