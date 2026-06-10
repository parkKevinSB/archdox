package com.archdox.legalai;

import java.util.List;

public record SourceBackedLegalReviewResult(
        SourceBackedLegalReviewStatus status,
        String summary,
        SourceBackedLegalReviewConfidence confidence,
        String legalReviewScope,
        String passReason,
        String limitations,
        List<String> reviewedReferenceIds,
        List<SourceBackedLegalReviewIssue> issues
) {
    public SourceBackedLegalReviewResult {
        status = status == null ? SourceBackedLegalReviewStatus.INSUFFICIENT_CONTEXT : status;
        summary = normalize(summary);
        confidence = confidence == null ? SourceBackedLegalReviewConfidence.MEDIUM : confidence;
        legalReviewScope = normalize(legalReviewScope);
        passReason = normalize(passReason);
        limitations = normalize(limitations);
        reviewedReferenceIds = normalizeList(reviewedReferenceIds, 80);
        issues = issues == null ? List.of() : List.copyOf(issues);

        if (status == SourceBackedLegalReviewStatus.PASS && reviewedReferenceIds.isEmpty()) {
            throw new IllegalArgumentException("PASS legal review requires reviewedReferenceIds");
        }
        if (status == SourceBackedLegalReviewStatus.PASS && passReason.isBlank()) {
            throw new IllegalArgumentException("PASS legal review requires passReason");
        }
        if ((status == SourceBackedLegalReviewStatus.WARN || status == SourceBackedLegalReviewStatus.FAIL)
                && issues.isEmpty()) {
            throw new IllegalArgumentException("WARN or FAIL legal review requires issues");
        }
        if (status == SourceBackedLegalReviewStatus.INSUFFICIENT_CONTEXT && limitations.isBlank()) {
            throw new IllegalArgumentException("INSUFFICIENT_CONTEXT legal review requires limitations");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> normalizeList(List<String> values, int limit) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(SourceBackedLegalReviewResult::normalize)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(limit)
                .toList();
    }
}
