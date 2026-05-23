package com.archdox.cloud.checklist.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record SaveChecklistAnswerRequest(
        Long targetId,
        @NotNull Map<String, Object> answer,
        @Size(max = 2000) String note
) {
}
