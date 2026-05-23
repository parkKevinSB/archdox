package com.archdox.cloud.assignment.dto;

import com.archdox.cloud.assignment.domain.ReportAssignmentRole;
import jakarta.validation.constraints.NotNull;

public record UpsertReportAssignmentRequest(
        @NotNull Long userId,
        @NotNull ReportAssignmentRole role
) {
}
