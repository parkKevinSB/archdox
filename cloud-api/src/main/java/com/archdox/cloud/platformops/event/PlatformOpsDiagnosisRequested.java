package com.archdox.cloud.platformops.event;

public record PlatformOpsDiagnosisRequested(
        Long opsRunId,
        Long incidentId,
        Long requestedByUserId
) {
}
