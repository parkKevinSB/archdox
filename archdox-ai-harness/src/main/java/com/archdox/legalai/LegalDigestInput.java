package com.archdox.legalai;

import java.util.List;
import java.util.Objects;

public record LegalDigestInput(
        String changeSetId,
        String actCode,
        String actName,
        String actType,
        String sourceCode,
        String effectiveDate,
        String detectedAt,
        String sourceSummary,
        List<LegalDigestArticleChange> articleChanges
) {
    public LegalDigestInput {
        changeSetId = requireText(changeSetId, "changeSetId");
        actCode = requireText(actCode, "actCode");
        actName = requireText(actName, "actName");
        actType = normalize(actType);
        sourceCode = requireText(sourceCode, "sourceCode");
        effectiveDate = normalize(effectiveDate);
        detectedAt = normalize(detectedAt);
        sourceSummary = normalize(sourceSummary);
        articleChanges = articleChanges == null ? List.of() : List.copyOf(articleChanges);
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
