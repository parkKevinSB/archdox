package com.archdox.cloud.checklistprint.dto;

import java.util.List;

public record ChecklistPrintDocumentResponse(
        String checklistType,
        String title,
        String documentNo,
        String supervisionWorkMode,
        String supervisionWorkModeName,
        String tradeGroupCode,
        String tradeGroupName,
        String tradeCode,
        String tradeName,
        String subTradeCode,
        String subTradeName,
        String constructionPhaseCode,
        String constructionPhaseName,
        String floorArea,
        String location,
        int totalRowCount,
        int checkedRowCount,
        List<ChecklistPrintRowResponse> rows
) {
    public ChecklistPrintDocumentResponse {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
