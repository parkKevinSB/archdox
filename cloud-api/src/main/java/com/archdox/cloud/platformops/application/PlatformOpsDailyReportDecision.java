package com.archdox.cloud.platformops.application;

import java.time.OffsetDateTime;

public record PlatformOpsDailyReportDecision(
        String status,
        String reason,
        OffsetDateTime dueAt,
        Long opsRunId
) {
    public static PlatformOpsDailyReportDecision skipped(String reason, OffsetDateTime dueAt) {
        return new PlatformOpsDailyReportDecision("SKIPPED", reason, dueAt, null);
    }

    public static PlatformOpsDailyReportDecision requested(OffsetDateTime dueAt, Long opsRunId) {
        return new PlatformOpsDailyReportDecision("REQUESTED", "DUE", dueAt, opsRunId);
    }

}
