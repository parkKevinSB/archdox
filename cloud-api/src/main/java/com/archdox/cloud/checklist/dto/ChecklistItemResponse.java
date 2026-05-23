package com.archdox.cloud.checklist.dto;

import com.archdox.cloud.checklist.domain.ChecklistAnswerType;
import java.util.List;

public record ChecklistItemResponse(
        Long id,
        String itemCode,
        String label,
        String description,
        ChecklistAnswerType answerType,
        boolean required,
        int displayOrder,
        List<String> options
) {
}
