package com.archdox.cloud.checklist.dto;

import java.util.List;
import java.util.Map;

public record ChecklistSchemaResponse(
        Long id,
        Long officeId,
        String reportType,
        String siteType,
        String targetType,
        String code,
        String name,
        int version,
        Map<String, Object> schema,
        List<ChecklistItemResponse> items
) {
}
