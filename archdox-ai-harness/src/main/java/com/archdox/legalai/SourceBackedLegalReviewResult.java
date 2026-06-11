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
        if (status == SourceBackedLegalReviewStatus.PASS
                && containsFinalComplianceWording(summary + "\n" + legalReviewScope + "\n" + passReason)) {
            throw new IllegalArgumentException("PASS legal review must not use final legal compliance wording");
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

    private static boolean containsFinalComplianceWording(String value) {
        var normalized = normalize(value).replace(" ", "");
        if (normalized.isBlank()) {
            return false;
        }
        return normalized.contains("법적요구사항을충족")
                || normalized.contains("법적요건을충족")
                || normalized.contains("법령요건을충족")
                || normalized.contains("법령에부합")
                || normalized.contains("법에부합")
                || normalized.contains("법령을준수")
                || normalized.contains("법을준수")
                || normalized.contains("위반사항없")
                || normalized.contains("법적위험이없")
                || normalized.contains("법률리스크가없");
    }
}
