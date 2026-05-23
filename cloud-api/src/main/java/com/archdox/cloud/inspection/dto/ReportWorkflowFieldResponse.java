package com.archdox.cloud.inspection.dto;

public record ReportWorkflowFieldResponse(
        String key,
        String label,
        String type,
        String placeholder,
        boolean required
) {
}
