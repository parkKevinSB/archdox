package com.archdox.legalai;

import com.archdox.documentai.ReportPreflightFindingSummary;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SourceBackedLegalReviewInput(
        String officeId,
        String reportId,
        String reportType,
        String title,
        int contentRevision,
        Map<String, Object> reportSnapshot,
        Map<String, Object> steps,
        List<ReportPreflightFindingSummary> deterministicFindings,
        List<Map<String, Object>> sourceBackedLegalReferences,
        Map<String, Object> legalReviewContext
) {
    public SourceBackedLegalReviewInput {
        officeId = requireText(officeId, "officeId");
        reportId = requireText(reportId, "reportId");
        reportType = requireText(reportType, "reportType");
        title = normalize(title);
        reportSnapshot = reportSnapshot == null ? Map.of() : Map.copyOf(reportSnapshot);
        steps = steps == null ? Map.of() : Map.copyOf(steps);
        deterministicFindings = deterministicFindings == null ? List.of() : List.copyOf(deterministicFindings);
        sourceBackedLegalReferences = sourceBackedLegalReferences == null
                ? List.of()
                : sourceBackedLegalReferences.stream()
                .map(reference -> reference == null ? Map.<String, Object>of() : Map.copyOf(reference))
                .toList();
        legalReviewContext = legalReviewContext == null ? Map.of() : Map.copyOf(legalReviewContext);
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
