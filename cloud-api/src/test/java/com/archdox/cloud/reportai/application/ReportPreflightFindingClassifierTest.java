package com.archdox.cloud.reportai.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.archdox.cloud.reportai.domain.ReportPreflightFindingResolutionStatus;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewFinding;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportPreflightFindingClassifierTest {
    @Test
    void displayOnlyLegalSummaryDoesNotRequireResolutionAndAutoResolves() {
        var now = OffsetDateTime.parse("2026-06-10T09:00:00+09:00");
        var finding = finding(
                "LEGAL_REVIEW",
                "LEGAL_REVIEW_BLOCKED",
                "HIGH",
                Map.of("approvalRequired", "true"));

        assertThat(ReportPreflightFindingClassifier.requiresResolutionForGeneration(finding)).isFalse();

        ReportPreflightFindingClassifier.autoResolveOnCreate(finding, 7L, now);

        assertThat(finding.resolutionStatus()).isEqualTo(ReportPreflightFindingResolutionStatus.RESOLVED);
        assertThat(finding.resolutionNote()).isEqualTo("DISPLAY_ONLY_LEGAL_REVIEW_SUMMARY");
        assertThat(finding.resolvedBy()).isEqualTo(7L);
        assertThat(finding.resolvedAt()).isEqualTo(now);
    }

    @Test
    void insufficientLegalContextStillRequiresResolution() {
        var finding = finding(
                "LEGAL_REVIEW",
                "LEGAL_REVIEW_INSUFFICIENT_CONTEXT",
                "MEDIUM",
                Map.of("approvalRequired", "true"));

        assertThat(ReportPreflightFindingClassifier.requiresResolutionForGeneration(finding)).isTrue();
    }

    @Test
    void aiWordingFindingStillRequiresResolution() {
        var finding = finding(
                "AI",
                "WORDING",
                "LOW",
                Map.of("category", "WORDING"));

        assertThat(ReportPreflightFindingClassifier.requiresResolutionForGeneration(finding)).isTrue();
    }

    private ReportPreflightReviewFinding finding(
            String source,
            String code,
            String severity,
            Map<String, String> attributes
    ) {
        return new ReportPreflightReviewFinding(
                10L,
                20L,
                30L,
                source,
                code,
                severity,
                "LEGAL_REVIEW",
                "message",
                "evidence",
                attributes,
                OffsetDateTime.parse("2026-06-10T08:00:00+09:00"));
    }
}
