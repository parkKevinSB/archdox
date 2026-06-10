package com.archdox.cloud.reportai.application;

import com.archdox.cloud.reportai.domain.ReportPreflightFindingResolutionStatus;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewFinding;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

final class ReportPreflightFindingClassifier {
    private static final String LEGAL_EVIDENCE_CONTEXT_USED = "LEGAL_EVIDENCE_CONTEXT_USED";
    private static final Set<String> DISPLAY_ONLY_LEGAL_REVIEW_CODES = Set.of(
            "LEGAL_REVIEW_PASSED",
            "LEGAL_REVIEW_NEEDS_HUMAN_REVIEW",
            "LEGAL_REVIEW_BLOCKED",
            "LEGAL_REVIEW_SKIPPED");

    private ReportPreflightFindingClassifier() {
    }

    static boolean requiresResolutionForGeneration(ReportPreflightFinding finding) {
        if (finding == null) {
            return false;
        }
        return requiresResolutionForGeneration(
                finding.source(),
                finding.code(),
                finding.severity(),
                finding.attributes());
    }

    static boolean requiresResolutionForGeneration(ReportPreflightReviewFinding finding) {
        if (finding == null) {
            return false;
        }
        return requiresResolutionForGeneration(
                finding.source(),
                finding.code(),
                finding.severity(),
                finding.attributesJson());
    }

    static boolean shouldAutoResolveOnCreate(ReportPreflightReviewFinding finding) {
        return isDisplayOnlyLegalFinding(finding.source(), finding.code());
    }

    static void autoResolveOnCreate(ReportPreflightReviewFinding finding, Long resolvedBy, OffsetDateTime now) {
        if (!shouldAutoResolveOnCreate(finding)) {
            return;
        }
        finding.resolve(
                ReportPreflightFindingResolutionStatus.RESOLVED,
                "DISPLAY_ONLY_LEGAL_REVIEW_SUMMARY",
                resolvedBy,
                now);
    }

    private static boolean requiresResolutionForGeneration(
            String source,
            String code,
            String severity,
            Map<String, String> attributes
    ) {
        var safeAttributes = attributes == null ? Map.<String, String>of() : attributes;
        if (isDisplayOnlyLegalFinding(source, code)) {
            return false;
        }
        if (isBlockingSeverity(severity)) {
            return true;
        }
        if ("AI".equals(source)) {
            return true;
        }
        return Boolean.parseBoolean(safeAttributes.getOrDefault("approvalRequired", "false"));
    }

    private static boolean isDisplayOnlyLegalFinding(String source, String code) {
        if (LEGAL_EVIDENCE_CONTEXT_USED.equals(code)) {
            return true;
        }
        return "LEGAL_REVIEW".equals(source) && DISPLAY_ONLY_LEGAL_REVIEW_CODES.contains(code);
    }

    private static boolean isBlockingSeverity(String severity) {
        return "HIGH".equals(severity) || "CRITICAL".equals(severity);
    }
}
