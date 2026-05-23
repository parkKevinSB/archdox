package com.archdox.cloud.checklist.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record ChecklistAnswerResponse(
        Long id,
        Long reportId,
        Long checklistSchemaId,
        Long checklistItemId,
        String itemCode,
        Long targetId,
        Map<String, Object> answer,
        String note,
        int clientRevision,
        OffsetDateTime savedAt
) {
}
