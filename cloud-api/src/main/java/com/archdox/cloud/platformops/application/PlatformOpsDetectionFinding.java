package com.archdox.cloud.platformops.application;

import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import java.util.Map;

public record PlatformOpsDetectionFinding(
        Long officeId,
        PlatformOpsFindingSeverity severity,
        String category,
        String code,
        String title,
        String message,
        String resourceType,
        String resourceId,
        String workflowType,
        String workflowKey,
        Map<String, Object> evidenceJson,
        String recommendation
) {
}
