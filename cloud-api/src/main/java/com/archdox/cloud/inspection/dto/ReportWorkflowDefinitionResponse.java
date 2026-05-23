package com.archdox.cloud.inspection.dto;

import java.util.List;

public record ReportWorkflowDefinitionResponse(
        Long reportId,
        Long officeId,
        String reportType,
        String siteType,
        String targetType,
        String flowId,
        String title,
        String source,
        Long definitionId,
        Long revisionId,
        Integer version,
        Long checklistSchemaId,
        String checklistSchemaCode,
        Integer checklistSchemaVersion,
        List<ReportWorkflowStepResponse> steps
) {
}
