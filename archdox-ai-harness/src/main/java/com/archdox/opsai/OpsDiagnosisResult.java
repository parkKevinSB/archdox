package com.archdox.opsai;

import java.util.List;

public record OpsDiagnosisResult(
        OpsDiagnosisStatus status,
        String summary,
        String confidence,
        List<OpsDiagnosisIssue> issues
) {
    public OpsDiagnosisResult {
        status = status == null ? OpsDiagnosisStatus.NEEDS_ATTENTION : status;
        summary = summary == null ? "" : summary.trim();
        confidence = confidence == null || confidence.isBlank() ? "MEDIUM" : confidence.trim();
        issues = issues == null ? List.of() : List.copyOf(issues);
        if (status == OpsDiagnosisStatus.CLEAR && !issues.isEmpty()) {
            throw new IllegalArgumentException("CLEAR status cannot contain issues");
        }
    }
}
