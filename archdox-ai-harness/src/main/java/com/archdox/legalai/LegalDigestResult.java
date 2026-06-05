package com.archdox.legalai;

import java.util.List;

public record LegalDigestResult(
        LegalDigestStatus status,
        String title,
        String summary,
        String impactSummary,
        String confidence,
        List<String> affectedReportTypes,
        List<String> affectedCatalogItems,
        List<String> keyArticles,
        String reviewNotes
) {
    public LegalDigestResult {
        status = status == null ? LegalDigestStatus.NEEDS_HUMAN_REVIEW : status;
        title = normalize(title);
        summary = normalize(summary);
        impactSummary = normalize(impactSummary);
        confidence = confidence == null || confidence.isBlank() ? "MEDIUM" : confidence.trim();
        affectedReportTypes = normalizeList(affectedReportTypes);
        affectedCatalogItems = normalizeList(affectedCatalogItems);
        keyArticles = normalizeList(keyArticles);
        reviewNotes = normalize(reviewNotes);
        if (status == LegalDigestStatus.PUBLISHABLE && (title.isBlank() || summary.isBlank())) {
            throw new IllegalArgumentException("PUBLISHABLE legal digest result requires title and summary");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(LegalDigestResult::normalize)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(30)
                .toList();
    }
}
