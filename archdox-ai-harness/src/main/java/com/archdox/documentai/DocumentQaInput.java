package com.archdox.documentai;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record DocumentQaInput(
        String officeId,
        String documentJobId,
        String reportId,
        String reportType,
        String title,
        String outputFormat,
        Map<String, Object> reportSnapshot,
        List<DocumentQaArtifactSummary> artifacts,
        String renderedText
) {
    public DocumentQaInput {
        officeId = requireText(officeId, "officeId");
        documentJobId = requireText(documentJobId, "documentJobId");
        reportId = requireText(reportId, "reportId");
        reportType = requireText(reportType, "reportType");
        title = title == null ? "" : title.trim();
        outputFormat = requireText(outputFormat, "outputFormat");
        reportSnapshot = reportSnapshot == null ? Map.of() : Map.copyOf(reportSnapshot);
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        renderedText = renderedText == null ? "" : renderedText.trim();
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
