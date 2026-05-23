package com.archdox.cloud.inspection.dto;

import com.archdox.cloud.inspection.domain.InspectionReportStatus;

public record InspectionReportResponse(
        Long id,
        Long officeId,
        Long projectId,
        Long siteId,
        String reportNo,
        String reportType,
        String title,
        InspectionReportStatus status,
        String currentStep,
        Long templateId,
        int contentRevision,
        Integer submittedRevision,
        Integer generatedRevision,
        Long lastDocumentJobId,
        boolean writeAllowed,
        boolean reopenAllowed
) {
}
