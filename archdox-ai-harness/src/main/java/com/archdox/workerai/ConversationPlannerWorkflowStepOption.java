package com.archdox.workerai;

import java.util.List;
import java.util.Objects;

public record ConversationPlannerWorkflowStepOption(
        String code,
        String title,
        String description,
        boolean saved,
        List<ConversationPlannerFieldOption> fields
) {
    public ConversationPlannerWorkflowStepOption {
        code = require(code, "code");
        title = clean(title);
        description = clean(description);
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim().toUpperCase();
    }

    private static String clean(String value) {
        return Objects.toString(value, "").trim();
    }
}
