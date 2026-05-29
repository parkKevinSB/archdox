package com.archdox.documentai;

import java.util.Objects;

public record DocumentQaArtifactSummary(
        String artifactType,
        String fileName,
        String mimeType,
        long bytes
) {
    public DocumentQaArtifactSummary {
        artifactType = normalize(artifactType, "UNKNOWN");
        fileName = normalize(fileName, "");
        mimeType = normalize(mimeType, "");
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes must not be negative");
        }
    }

    private static String normalize(String value, String fallback) {
        Objects.requireNonNull(fallback, "fallback must not be null");
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
