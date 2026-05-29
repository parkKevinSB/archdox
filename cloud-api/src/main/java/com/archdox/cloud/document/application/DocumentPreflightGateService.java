package com.archdox.cloud.document.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewStatus;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewRunRepository;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DocumentPreflightGateService {
    private final ReportPreflightReviewRunRepository preflightRunRepository;

    public DocumentPreflightGateService(ReportPreflightReviewRunRepository preflightRunRepository) {
        this.preflightRunRepository = preflightRunRepository;
    }

    public void requirePassedForGeneration(InspectionReport report) {
        var reportRevision = report.generationRevision();
        var passed = preflightRunRepository.existsByOfficeIdAndReportIdAndReportRevisionAndStatus(
                report.officeId(),
                report.id(),
                reportRevision,
                ReportPreflightReviewStatus.PASSED);
        if (passed) {
            return;
        }
        var latestPassed = preflightRunRepository
                .findFirstByOfficeIdAndReportIdAndStatusOrderByReportRevisionDescRequestedAtDesc(
                        report.officeId(),
                        report.id(),
                        ReportPreflightReviewStatus.PASSED);
        if (latestPassed.isPresent()) {
            throw new BadRequestException(
                    "REPORT_PREFLIGHT_REVIEW_STALE",
                    "errors.document.preflightReviewStale",
                    "Latest report revision must pass preflight review before document generation.",
                    Map.of(
                            "reportId", report.id(),
                            "requiredRevision", reportRevision,
                            "latestPassedRevision", latestPassed.get().reportRevision()));
        }
        throw new BadRequestException(
                "REPORT_PREFLIGHT_REVIEW_REQUIRED",
                "errors.document.preflightReviewRequired",
                "Preflight review must pass before document generation.",
                Map.of(
                        "reportId", report.id(),
                        "requiredRevision", reportRevision));
    }
}
