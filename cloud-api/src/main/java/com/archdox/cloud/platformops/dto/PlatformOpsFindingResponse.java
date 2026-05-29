package com.archdox.cloud.platformops.dto;

import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSource;
import java.time.OffsetDateTime;
import java.util.Map;

public record PlatformOpsFindingResponse(
        Long id,
        Long incidentId,
        Long runId,
        Long officeId,
        PlatformOpsFindingSeverity severity,
        PlatformOpsFindingSource source,
        String code,
        String category,
        String title,
        String message,
        String resourceType,
        String resourceId,
        String workflowType,
        String workflowKey,
        Map<String, Object> evidenceJson,
        String recommendation,
        OffsetDateTime createdAt
) {
}
