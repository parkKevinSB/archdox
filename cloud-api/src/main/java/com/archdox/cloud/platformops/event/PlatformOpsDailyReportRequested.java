package com.archdox.cloud.platformops.event;

import java.time.OffsetDateTime;

public record PlatformOpsDailyReportRequested(
        Long opsRunId,
        OffsetDateTime dueAt,
        Long requestedByUserId
) {
}
