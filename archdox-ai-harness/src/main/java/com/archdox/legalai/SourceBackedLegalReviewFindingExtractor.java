package com.archdox.legalai;

import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFindingSeverity;
import io.github.parkkevinsb.flower.ai.harness.finding.FindingExtractor;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SourceBackedLegalReviewFindingExtractor implements FindingExtractor<SourceBackedLegalReviewResult> {
    @Override
    public List<AiFinding> extract(SourceBackedLegalReviewResult value, AiHarnessRunContext ctx) {
        return value.issues().stream()
                .map(issue -> new AiFinding(
                        issue.code(),
                        severity(issue.severity()),
                        issue.message(),
                        issue.evidence(),
                        issue.location(),
                        attributes(value, issue)))
                .toList();
    }

    private static AiFindingSeverity severity(SourceBackedLegalReviewIssueSeverity severity) {
        return switch (severity) {
            case INFO -> AiFindingSeverity.INFO;
            case LOW -> AiFindingSeverity.LOW;
            case MEDIUM -> AiFindingSeverity.MEDIUM;
            case HIGH -> AiFindingSeverity.HIGH;
            case CRITICAL -> AiFindingSeverity.CRITICAL;
        };
    }

    private static Map<String, String> attributes(
            SourceBackedLegalReviewResult result,
            SourceBackedLegalReviewIssue issue
    ) {
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("source", "LEGAL_REVIEW");
        attributes.put("category", issue.category().name());
        attributes.put("legalReviewStatus", result.status().name());
        attributes.put("confidence", result.confidence().name());
        attributes.put("draftOnly", "true");
        attributes.put("approvalRequired", "true");
        if (!issue.suggestion().isBlank()) {
            attributes.put("suggestion", issue.suggestion());
        }
        if (!issue.replacement().isBlank()) {
            attributes.put("replacement", issue.replacement());
        }
        if (!issue.relatedFieldPath().isBlank()) {
            attributes.put("relatedFieldPath", issue.relatedFieldPath());
        }
        if (!issue.legalReferenceIds().isEmpty()) {
            attributes.put("legalReferences", String.join(",", issue.legalReferenceIds()));
        }
        return attributes;
    }
}
