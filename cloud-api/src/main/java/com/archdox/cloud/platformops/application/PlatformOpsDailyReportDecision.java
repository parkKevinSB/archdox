package com.archdox.cloud.platformops.application;

import java.time.OffsetDateTime;

public record PlatformOpsDailyReportDecision(
        String status,
        String reason,
        OffsetDateTime dueAt,
        Long opsRunId,
        String reportPath
) {
    public static PlatformOpsDailyReportDecision skipped(String reason, OffsetDateTime dueAt) {
        return new PlatformOpsDailyReportDecision("SKIPPED", reason, dueAt, null, null);
    }

    public static PlatformOpsDailyReportDecision generated(
            OffsetDateTime dueAt,
            Long opsRunId,
            String reportPath
    ) {
        return new PlatformOpsDailyReportDecision("GENERATED", "DUE", dueAt, opsRunId, reportPath);
    }
}
