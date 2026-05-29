package com.archdox.documentai;

import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFindingSeverity;
import io.github.parkkevinsb.flower.ai.harness.finding.FindingExtractor;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DocumentQaFindingExtractor implements FindingExtractor<DocumentQaResult> {
    @Override
    public List<AiFinding> extract(DocumentQaResult value, AiHarnessRunContext ctx) {
        return value.issues().stream()
                .map(issue -> new AiFinding(
                        issue.code(),
                        toFindingSeverity(issue.severity()),
                        issue.message(),
                        issue.evidence(),
                        issue.location(),
                        attributes(value, issue)))
                .toList();
    }

    private static AiFindingSeverity toFindingSeverity(DocumentQaIssueSeverity severity) {
        return switch (severity) {
            case INFO -> AiFindingSeverity.INFO;
            case LOW -> AiFindingSeverity.LOW;
            case MEDIUM -> AiFindingSeverity.MEDIUM;
            case HIGH -> AiFindingSeverity.HIGH;
            case CRITICAL -> AiFindingSeverity.CRITICAL;
        };
    }

    private static Map<String, String> attributes(DocumentQaResult result, DocumentQaIssue issue) {
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("reviewStatus", result.status().name());
        attributes.put("confidence", result.confidence());
        if (!issue.suggestion().isBlank()) {
            attributes.put("suggestion", issue.suggestion());
        }
        return attributes;
    }
}
