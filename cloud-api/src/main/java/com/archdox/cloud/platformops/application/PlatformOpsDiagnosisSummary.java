package com.archdox.cloud.platformops.application;

import java.time.OffsetDateTime;

public record PlatformOpsDiagnosisSummary(
        Long opsRunId,
        Long incidentId,
        int findingCount,
        int operationEventCount,
        OffsetDateTime diagnosedAt
) {
}
