package com.archdox.workerai;

import java.util.Objects;

public record ConversationPlannerFieldOption(
        String key,
        String label,
        String type,
        boolean required
) {
    public ConversationPlannerFieldOption {
        key = require(key, "key");
        label = clean(label);
        type = clean(type);
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
