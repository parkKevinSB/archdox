package com.archdox.cloud.documenttype.dto;

import com.archdox.cloud.inspection.dto.ReportWorkflowStepResponse;
import java.util.List;

public record DocumentTypeResponse(
        Long id,
        Long officeId,
        String code,
        String reportType,
        String name,
        String description,
        String category,
        String defaultTemplateCode,
        String defaultTemplateStorageRef,
        String checklistSchemaCode,
        String defaultOutputFormat,
        int displayOrder,
        List<ReportWorkflowStepResponse> steps
) {
}
