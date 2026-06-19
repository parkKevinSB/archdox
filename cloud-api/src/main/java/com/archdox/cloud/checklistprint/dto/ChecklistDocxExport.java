package com.archdox.cloud.checklistprint.dto;

public record ChecklistDocxExport(
        String fileName,
        byte[] content
) {
}
