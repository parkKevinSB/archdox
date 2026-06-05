package com.archdox.legalai;

import java.util.Objects;

public record LegalDigestArticleChange(
        String articleKey,
        String articleTitle,
        String changeType,
        String beforeText,
        String afterText,
        String sourceVersionId,
        String effectiveDate,
        String sourceUrl
) {
    private static final int MAX_TEXT_CHARS = 4_000;

    public LegalDigestArticleChange {
        articleKey = requireText(articleKey, "articleKey");
        articleTitle = normalize(articleTitle);
        changeType = requireText(changeType, "changeType");
        beforeText = limitText(beforeText);
        afterText = limitText(afterText);
        sourceVersionId = normalize(sourceVersionId);
        effectiveDate = normalize(effectiveDate);
        sourceUrl = normalize(sourceUrl);
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

    private static String limitText(String value) {
        var normalized = normalize(value);
        if (normalized.length() <= MAX_TEXT_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_TEXT_CHARS) + "...";
    }
}
