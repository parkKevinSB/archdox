package com.archdox.documentai;

import java.util.Objects;

public record DocumentQaIssue(
        String code,
        DocumentQaIssueSeverity severity,
        String location,
        String message,
        String evidence,
        String suggestion
) {
    public DocumentQaIssue {
        code = requireText(code, "code");
        Objects.requireNonNull(severity, "severity must not be null");
        location = location == null ? "" : location.trim();
        message = requireText(message, "message");
        evidence = evidence == null ? "" : evidence.trim();
        suggestion = suggestion == null ? "" : suggestion.trim();
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
