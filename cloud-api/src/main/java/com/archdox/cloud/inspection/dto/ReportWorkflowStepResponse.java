package com.archdox.cloud.inspection.dto;

import java.util.List;

public record ReportWorkflowStepResponse(
        String code,
        String title,
        String description,
        String stepType,
        String savePolicy,
        List<ReportWorkflowFieldResponse> fields
) {
}
