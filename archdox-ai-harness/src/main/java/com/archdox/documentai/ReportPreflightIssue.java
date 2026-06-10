package com.archdox.documentai;

import java.util.Objects;

public record ReportPreflightIssue(
        String code,
        ReportPreflightIssueCategory category,
        ReportPreflightIssueSeverity severity,
        String location,
        String message,
        String evidence,
        String suggestion,
        String replacement
) {
    public ReportPreflightIssue {
        code = requireText(code, "code");
        category = category == null ? ReportPreflightIssueCategory.GENERAL : category;
        Objects.requireNonNull(severity, "severity must not be null");
        location = location == null ? "" : location.trim();
        message = requireText(message, "message");
        evidence = evidence == null ? "" : evidence.trim();
        suggestion = suggestion == null ? "" : suggestion.trim();
        replacement = replacement == null ? "" : replacement.trim();
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
