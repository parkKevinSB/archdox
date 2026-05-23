package com.archdox.cloud.inspection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInspectionReportRequest(
        @NotNull Long projectId,
        Long siteId,
        @NotBlank @Size(max = 100) String reportType,
        @Size(max = 200) String title,
        Long templateId
) {
}
