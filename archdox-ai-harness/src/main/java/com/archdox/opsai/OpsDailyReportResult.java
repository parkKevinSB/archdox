package com.archdox.opsai;

import java.util.List;

public record OpsDailyReportResult(
        OpsDailyReportStatus status,
        String summary,
        String confidence,
        List<String> pLikeCurrentFindings,
        List<String> iLikeAccumulatedSignals,
        List<String> dLikeTrendSignals,
        List<String> recommendations,
        List<OpsDailyReportIssue> issues
) {
    public OpsDailyReportResult {
        status = status == null ? OpsDailyReportStatus.WATCH : status;
        summary = summary == null ? "" : summary.trim();
        confidence = confidence == null || confidence.isBlank() ? "MEDIUM" : confidence.trim();
        pLikeCurrentFindings = copyStrings(pLikeCurrentFindings);
        iLikeAccumulatedSignals = copyStrings(iLikeAccumulatedSignals);
        dLikeTrendSignals = copyStrings(dLikeTrendSignals);
        recommendations = copyStrings(recommendations);
        issues = issues == null ? List.of() : List.copyOf(issues);
        if (status == OpsDailyReportStatus.CLEAR && !issues.isEmpty()) {
            throw new IllegalArgumentException("CLEAR status cannot contain issues");
        }
    }

    private static List<String> copyStrings(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }
}
