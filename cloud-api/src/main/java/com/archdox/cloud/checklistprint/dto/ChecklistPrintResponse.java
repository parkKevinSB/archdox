package com.archdox.cloud.checklistprint.dto;

import java.util.List;

public record ChecklistPrintResponse(
        Long reportId,
        String reportNo,
        String reportTitle,
        String reportType,
        String checklistType,
        String checklistTypeName,
        int documentCount,
        int checkedRowCount,
        List<ChecklistPrintDocumentResponse> documents,
        String html
) {
    public ChecklistPrintResponse {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }
}
