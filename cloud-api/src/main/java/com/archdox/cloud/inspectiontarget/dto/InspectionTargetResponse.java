package com.archdox.cloud.inspectiontarget.dto;

import com.archdox.cloud.inspectiontarget.domain.InspectionTargetStatus;
import java.util.Map;

public record InspectionTargetResponse(
        Long id,
        Long officeId,
        Long projectId,
        Long siteId,
        Long parentTargetId,
        String targetType,
        String code,
        String name,
        String address,
        Map<String, Object> metadata,
        InspectionTargetStatus status
) {
}
