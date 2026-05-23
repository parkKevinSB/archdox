package com.archdox.cloud.inspectiontarget.dto;

import com.archdox.cloud.inspectiontarget.domain.InspectionReportTargetRole;
import java.time.OffsetDateTime;
import java.util.Map;

public record InspectionReportTargetResponse(
        Long id,
        Long officeId,
        Long reportId,
        Long targetId,
        InspectionReportTargetRole role,
        Map<String, Object> snapshot,
        OffsetDateTime createdAt
) {
}
