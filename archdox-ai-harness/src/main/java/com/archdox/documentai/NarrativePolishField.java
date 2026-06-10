package com.archdox.documentai;

import java.util.Objects;

public record NarrativePolishField(
        String path,
        String label,
        String originalText
) {
    public NarrativePolishField {
        path = requireText(path, "path");
        label = label == null || label.isBlank() ? path : label.trim();
        originalText = originalText == null ? "" : originalText.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return Objects.requireNonNull(value).trim();
    }
}
