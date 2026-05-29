package com.archdox.documentai;

import java.util.List;
import java.util.Objects;

public record ReportPreflightResult(
        ReportPreflightStatus status,
        String summary,
        String confidence,
        List<ReportPreflightIssue> issues
) {
    public ReportPreflightResult {
        Objects.requireNonNull(status, "status must not be null");
        summary = summary == null ? "" : summary.trim();
        confidence = confidence == null || confidence.isBlank() ? "MEDIUM" : confidence.trim();
        issues = issues == null ? List.of() : List.copyOf(issues);
        if (status == ReportPreflightStatus.PASS && !issues.isEmpty()) {
            throw new IllegalArgumentException("PASS result must not contain issues");
        }
    }
}
