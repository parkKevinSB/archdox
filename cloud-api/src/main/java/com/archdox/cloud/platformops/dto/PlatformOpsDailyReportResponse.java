package com.archdox.cloud.platformops.dto;

import com.archdox.cloud.platformops.domain.PlatformOpsDailyReport;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record PlatformOpsDailyReportResponse(
        Long id,
        Long runId,
        OffsetDateTime dueAt,
        OffsetDateTime periodFrom,
        OffsetDateTime periodTo,
        String status,
        PlatformOpsFindingSeverity severity,
        String title,
        String summary,
        String reportPath,
        String aiHarnessRunId,
        List<String> pLikeSignals,
        List<String> iLikeSignals,
        List<String> dLikeSignals,
        List<String> recommendations,
        Map<String, Object> evidence,
        OffsetDateTime createdAt
) {
    public static PlatformOpsDailyReportResponse from(PlatformOpsDailyReport report) {
        return new PlatformOpsDailyReportResponse(
                report.id(),
                report.runId(),
                report.dueAt(),
                report.periodFrom(),
                report.periodTo(),
                report.status(),
                report.severity(),
                report.title(),
                report.summary(),
                report.reportPath(),
                report.aiHarnessRunId(),
                report.pLikeJson(),
                report.iLikeJson(),
                report.dLikeJson(),
                report.recommendationsJson(),
                report.evidenceJson(),
                report.createdAt());
    }
}
