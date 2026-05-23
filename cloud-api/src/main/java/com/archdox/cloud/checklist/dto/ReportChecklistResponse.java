package com.archdox.cloud.checklist.dto;

import java.util.List;

public record ReportChecklistResponse(
        ChecklistSchemaResponse schema,
        List<ChecklistAnswerResponse> answers
) {
}
