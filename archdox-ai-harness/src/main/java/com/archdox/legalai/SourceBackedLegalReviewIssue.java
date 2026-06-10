package com.archdox.legalai;

import java.util.List;
import java.util.Objects;

public record SourceBackedLegalReviewIssue(
        String code,
        SourceBackedLegalReviewIssueCategory category,
        SourceBackedLegalReviewIssueSeverity severity,
        String location,
        String message,
        String evidence,
        String suggestion,
        List<String> legalReferenceIds,
        String relatedFieldPath
) {
    public SourceBackedLegalReviewIssue {
        code = requireText(code, "code");
        category = category == null ? SourceBackedLegalReviewIssueCategory.LEGAL_RISK : category;
        severity = severity == null ? SourceBackedLegalReviewIssueSeverity.MEDIUM : severity;
        location = normalize(location);
        message = requireText(message, "message");
        evidence = normalize(evidence);
        suggestion = normalize(suggestion);
        legalReferenceIds = legalReferenceIds == null
                ? List.of()
                : legalReferenceIds.stream()
                .map(SourceBackedLegalReviewIssue::normalize)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(20)
                .toList();
        relatedFieldPath = normalize(relatedFieldPath);
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        var trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
