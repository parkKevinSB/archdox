package com.archdox.cloud.inspectiontarget.dto;

import com.archdox.cloud.inspectiontarget.domain.InspectionReportTargetRole;
import jakarta.validation.constraints.NotNull;

public record AttachInspectionReportTargetRequest(
        @NotNull Long targetId,
        InspectionReportTargetRole role
) {
    public InspectionReportTargetRole normalizedRole() {
        return role == null ? InspectionReportTargetRole.PRIMARY : role;
    }
}
