package com.archdox.cloud.inspection.dto;

public record ReportSubmitValidationIssueResponse(
        String code,
        String message,
        String resourceType,
        String resourceKey
) {
}
