package com.archdox.workerai;

import java.util.Objects;

public record ConversationPlannerEntityOption(
        String kind,
        String id,
        String label,
        String description
) {
    public ConversationPlannerEntityOption {
        kind = require(kind, "kind");
        id = require(id, "id");
        label = clean(label);
        description = clean(description);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String clean(String value) {
        return Objects.toString(value, "").trim();
    }
}
