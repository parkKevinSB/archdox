package com.archdox.documentai;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ReportPreflightInput(
        String officeId,
        String reportId,
        String reportType,
        String title,
        String status,
        int contentRevision,
        Map<String, Object> reportSnapshot,
        Map<String, Object> steps,
        List<Map<String, Object>> photos,
        List<ReportPreflightFindingSummary> deterministicFindings,
        List<Map<String, Object>> sourceBackedLegalReferences,
        Map<String, Object> legalReviewContext,
        String reviewMode
) {
    public ReportPreflightInput(
            String officeId,
            String reportId,
            String reportType,
            String title,
            String status,
            int contentRevision,
            Map<String, Object> reportSnapshot,
            Map<String, Object> steps,
            List<Map<String, Object>> photos,
            List<ReportPreflightFindingSummary> deterministicFindings
    ) {
        this(
                officeId,
                reportId,
                reportType,
                title,
                status,
                contentRevision,
                reportSnapshot,
                steps,
                photos,
                deterministicFindings,
                List.of(),
                Map.of(),
                "STANDARD_PREFLIGHT");
    }

    public ReportPreflightInput {
        officeId = requireText(officeId, "officeId");
        reportId = requireText(reportId, "reportId");
        reportType = requireText(reportType, "reportType");
        title = title == null ? "" : title.trim();
        status = requireText(status, "status");
        reportSnapshot = reportSnapshot == null ? Map.of() : Map.copyOf(reportSnapshot);
        steps = steps == null ? Map.of() : Map.copyOf(steps);
        photos = photos == null ? List.of() : photos.stream()
                .map(photo -> photo == null ? Map.<String, Object>of() : Map.copyOf(photo))
                .toList();
        deterministicFindings = deterministicFindings == null ? List.of() : List.copyOf(deterministicFindings);
        sourceBackedLegalReferences = sourceBackedLegalReferences == null
                ? List.of()
                : sourceBackedLegalReferences.stream()
                .map(reference -> reference == null ? Map.<String, Object>of() : Map.copyOf(reference))
                .toList();
        legalReviewContext = legalReviewContext == null ? Map.of() : Map.copyOf(legalReviewContext);
        reviewMode = reviewMode == null || reviewMode.isBlank() ? "STANDARD_PREFLIGHT" : reviewMode.trim();
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
