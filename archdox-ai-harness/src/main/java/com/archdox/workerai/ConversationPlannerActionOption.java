package com.archdox.workerai;

import java.util.Objects;

public record ConversationPlannerActionOption(
        String actionType,
        String label,
        String description,
        boolean requiresConfirmation
) {
    public ConversationPlannerActionOption {
        actionType = require(actionType, "actionType");
        label = clean(label);
        description = clean(description);
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
