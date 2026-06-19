package com.archdox.cloud.checklistprint.dto;

public record ChecklistPrintRowResponse(
        String workCategoryCode,
        String workCategoryName,
        String processCode,
        String processName,
        String inspectionItemCode,
        String inspectionItemName,
        String rowCode,
        String rowLabel,
        String basis,
        String referenceNote,
        String result,
        String actionNote,
        boolean checked
) {
}
