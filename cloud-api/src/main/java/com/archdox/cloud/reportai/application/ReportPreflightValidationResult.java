package com.archdox.cloud.reportai.application;

import java.util.List;

public record ReportPreflightValidationResult(
        List<ReportPreflightFinding> findings
) {
    public ReportPreflightValidationResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public boolean blocksGeneration() {
        return findings.stream().anyMatch(this::isBlocking);
    }

    public int blockingFindingCount() {
        return (int) findings.stream().filter(this::isBlocking).count();
    }

    private boolean isBlocking(ReportPreflightFinding finding) {
        return "HIGH".equals(finding.severity())
                || "CRITICAL".equals(finding.severity())
                || "AI".equals(finding.source())
                || Boolean.parseBoolean(finding.attributes().getOrDefault("approvalRequired", "false"));
    }
}
