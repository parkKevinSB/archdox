package com.archdox.cloud.reportai.application;

import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewFinding;
import com.archdox.cloud.reportai.flow.ReportPreflightReviewRequest;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewFindingRepository;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewRunRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportPreflightReviewFlowService {
    private final InspectionReportRepository reportRepository;
    private final ReportPreflightReviewRunRepository runRepository;
    private final ReportPreflightReviewFindingRepository findingRepository;
    private final ReportPreflightDeterministicValidator deterministicValidator;
    private final OperationEventService operationEventService;

    public ReportPreflightReviewFlowService(
            InspectionReportRepository reportRepository,
            ReportPreflightReviewRunRepository runRepository,
            ReportPreflightReviewFindingRepository findingRepository,
            ReportPreflightDeterministicValidator deterministicValidator,
            OperationEventService operationEventService
    ) {
        this.reportRepository = reportRepository;
        this.runRepository = runRepository;
        this.findingRepository = findingRepository;
        this.deterministicValidator = deterministicValidator;
        this.operationEventService = operationEventService;
    }

    @Transactional(readOnly = true)
    public void validateContext(ReportPreflightReviewRequest request) {
        reportRepository.findByIdAndOfficeId(request.reportId(), request.officeId())
                .orElseThrow(() -> new NotFoundException("Inspection report not found"));
        runRepository.findByIdAndOfficeIdAndReportId(request.reviewRunId(), request.officeId(), request.reportId())
                .orElseThrow(() -> new NotFoundException("Report preflight review run not found"));
    }

    @Transactional
    public ReportPreflightValidationResult runDeterministicValidation(ReportPreflightReviewRequest request) {
        var report = reportRepository.findByIdAndOfficeId(request.reportId(), request.officeId())
                .orElseThrow(() -> new NotFoundException("Inspection report not found"));
        var run = runRepository.findByIdAndOfficeIdAndReportId(request.reviewRunId(), request.officeId(), request.reportId())
                .orElseThrow(() -> new NotFoundException("Report preflight review run not found"));
        run.markRunning(OffsetDateTime.now());
        findingRepository.deleteByReviewRunId(run.id());
        var result = deterministicValidator.validate(report);
        for (var finding : result.findings()) {
            findingRepository.save(new ReportPreflightReviewFinding(
                    request.officeId(),
                    request.reviewRunId(),
                    request.reportId(),
                    finding.source(),
                    finding.code(),
                    finding.severity(),
                    finding.location(),
                    finding.message(),
                    finding.evidence(),
                    finding.attributes(),
                    OffsetDateTime.now()));
        }
        if (result.blocksGeneration()) {
            run.markNeedsAttention("DETERMINISTIC_PREFLIGHT_BLOCKED", OffsetDateTime.now());
        } else if (run.hasHarness()) {
            run.markRunning(OffsetDateTime.now());
        } else {
            run.markPassed("DETERMINISTIC_PREFLIGHT_PASSED", OffsetDateTime.now());
        }
        operationEventService.record(
                request.officeId(),
                result.blocksGeneration() ? OperationEventSeverity.WARN : OperationEventSeverity.INFO,
                "REPORT_PREFLIGHT_REVIEW_DETERMINISTIC_VALIDATION",
                "report-preflight-review",
                workflowKey(request),
                "REPORT_PREFLIGHT_REVIEW_RUN",
                request.reviewRunId(),
                request.requestedBy(),
                null,
                result.blocksGeneration()
                        ? "Report preflight validation found blocking issues."
                        : "Report preflight validation passed.",
                Map.of(
                        "reportId", request.reportId(),
                        "reviewRunId", request.reviewRunId(),
                        "findingCount", result.findings().size(),
                        "blockingFindingCount", result.blockingFindingCount(),
                        "aiReviewPlanned", run.hasHarness(),
                        "aiReviewSkipped", result.blocksGeneration() || !run.hasHarness()));
        return result;
    }

    @Transactional(readOnly = true)
    public boolean canSubmitAiHarness(ReportPreflightReviewRequest request) {
        var run = runRepository.findByIdAndOfficeIdAndReportId(request.reviewRunId(), request.officeId(), request.reportId())
                .orElseThrow(() -> new NotFoundException("Report preflight review run not found"));
        return run.hasHarness() && !run.terminal() && !run.harnessTerminal();
    }

    @Transactional
    public void markAiHarnessSubmitted(ReportPreflightReviewRequest request) {
        operationEventService.record(
                request.officeId(),
                OperationEventSeverity.INFO,
                "REPORT_PREFLIGHT_AI_REVIEW_SUBMITTED",
                "report-preflight-review",
                workflowKey(request),
                "REPORT_PREFLIGHT_REVIEW_RUN",
                request.reviewRunId(),
                request.requestedBy(),
                null,
                "Report preflight review flow submitted the AI harness.",
                Map.of("reportId", request.reportId(), "reviewRunId", request.reviewRunId()));
    }

    @Transactional(readOnly = true)
    public boolean isAiHarnessTerminal(ReportPreflightReviewRequest request) {
        var run = runRepository.findByIdAndOfficeIdAndReportId(request.reviewRunId(), request.officeId(), request.reportId())
                .orElseThrow(() -> new NotFoundException("Report preflight review run not found"));
        if (!run.hasHarness()) {
            return true;
        }
        if (run.terminal() && !run.harnessTerminal()) {
            return true;
        }
        return run.harnessTerminal();
    }

    @Transactional
    public void summarizeAiResult(ReportPreflightReviewRequest request) {
        var run = runRepository.findByIdAndOfficeIdAndReportId(request.reviewRunId(), request.officeId(), request.reportId())
                .orElseThrow(() -> new NotFoundException("Report preflight review run not found"));
        if (!run.hasHarness()) {
            return;
        }
        if (run.status() == com.archdox.cloud.reportai.domain.ReportPreflightReviewStatus.FAILED) {
            return;
        }
        if (!run.harnessTerminal()) {
            return;
        }
        var findings = findingRepository.findByOfficeIdAndReviewRunIdOrderByIdAsc(request.officeId(), request.reviewRunId());
        long blockingCount = findings.stream().filter(finding -> isBlockingSeverity(finding.severity())).count();
        if (blockingCount > 0) {
            run.markNeedsAttention("AI_PREFLIGHT_NEEDS_ATTENTION", OffsetDateTime.now());
        } else {
            run.markPassed("AI_PREFLIGHT_PASSED", OffsetDateTime.now());
        }
        operationEventService.record(
                request.officeId(),
                blockingCount > 0 ? OperationEventSeverity.WARN : OperationEventSeverity.INFO,
                "REPORT_PREFLIGHT_AI_REVIEW_SUMMARIZED",
                "report-preflight-review",
                workflowKey(request),
                "REPORT_PREFLIGHT_REVIEW_RUN",
                request.reviewRunId(),
                request.requestedBy(),
                null,
                blockingCount > 0
                        ? "Report preflight AI review found issues needing attention."
                        : "Report preflight AI review passed.",
                Map.of(
                        "reportId", request.reportId(),
                        "reviewRunId", request.reviewRunId(),
                        "findingCount", findings.size(),
                        "blockingFindingCount", blockingCount));
    }

    @Transactional
    public void complete(ReportPreflightReviewRequest request) {
        operationEventService.record(
                request.officeId(),
                OperationEventSeverity.INFO,
                "REPORT_PREFLIGHT_REVIEW_COMPLETED",
                "report-preflight-review",
                workflowKey(request),
                "REPORT_PREFLIGHT_REVIEW_RUN",
                request.reviewRunId(),
                request.requestedBy(),
                null,
                "Report preflight review flow completed.",
                Map.of("reportId", request.reportId(), "reviewRunId", request.reviewRunId()));
    }

    @Transactional
    public void fail(ReportPreflightReviewRequest request, String reason) {
        runRepository.findByIdAndOfficeIdAndReportId(request.reviewRunId(), request.officeId(), request.reportId())
                .ifPresent(run -> run.markFailed(reasonOf(reason), OffsetDateTime.now()));
        operationEventService.record(
                request.officeId(),
                OperationEventSeverity.ERROR,
                "REPORT_PREFLIGHT_REVIEW_FAILED",
                "report-preflight-review",
                workflowKey(request),
                "REPORT_PREFLIGHT_REVIEW_RUN",
                request.reviewRunId(),
                request.requestedBy(),
                null,
                "Report preflight review flow failed.",
                Map.of(
                        "reportId", request.reportId(),
                        "reviewRunId", request.reviewRunId(),
                        "reason", reasonOf(reason)));
    }

    private static String workflowKey(ReportPreflightReviewRequest request) {
        return "report:" + request.reportId() + ":preflight-run:" + request.reviewRunId();
    }

    private static boolean isBlockingSeverity(String severity) {
        return "HIGH".equals(severity) || "CRITICAL".equals(severity);
    }

    private static String reasonOf(String reason) {
        if (reason != null && !reason.isBlank()) {
            return reason;
        }
        return "Report preflight review failed";
    }
}
