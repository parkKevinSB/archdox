package com.archdox.cloud.platformops.dto;

import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.domain.PlatformOpsIncidentStatus;
import java.time.OffsetDateTime;

public record PlatformOpsIncidentResponse(
        Long id,
        PlatformOpsIncidentStatus status,
        PlatformOpsFindingSeverity severity,
        String category,
        String title,
        String summary,
        Long officeId,
        String primaryResourceType,
        String primaryResourceId,
        OffsetDateTime firstSeenAt,
        OffsetDateTime lastSeenAt,
        OffsetDateTime resolvedAt,
        Long createdByRunId
) {
}
