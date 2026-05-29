package com.archdox.cloud.documentai.application;

import java.util.List;

public record DeterministicDocumentReviewResult(
        List<DeterministicDocumentReviewFinding> findings
) {
    public DeterministicDocumentReviewResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public boolean blocksAiReview() {
        return findings.stream().anyMatch(this::isBlocking);
    }

    public int blockingFindingCount() {
        return (int) findings.stream().filter(this::isBlocking).count();
    }

    private boolean isBlocking(DeterministicDocumentReviewFinding finding) {
        return "HIGH".equals(finding.severity()) || "CRITICAL".equals(finding.severity());
    }
}
