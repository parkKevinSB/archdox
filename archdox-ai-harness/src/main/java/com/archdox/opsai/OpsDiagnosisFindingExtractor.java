package com.archdox.opsai;

import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFindingSeverity;
import io.github.parkkevinsb.flower.ai.harness.finding.FindingExtractor;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OpsDiagnosisFindingExtractor implements FindingExtractor<OpsDiagnosisResult> {
    @Override
    public List<AiFinding> extract(OpsDiagnosisResult value, AiHarnessRunContext ctx) {
        return value.issues().stream()
                .map(issue -> new AiFinding(
                        issue.code(),
                        toFindingSeverity(issue.severity()),
                        issue.message(),
                        issue.evidence(),
                        issue.category(),
                        attributes(value, issue)))
                .toList();
    }

    private static AiFindingSeverity toFindingSeverity(OpsDiagnosisIssueSeverity severity) {
        return switch (severity) {
            case INFO -> AiFindingSeverity.INFO;
            case LOW -> AiFindingSeverity.LOW;
            case MEDIUM -> AiFindingSeverity.MEDIUM;
            case HIGH -> AiFindingSeverity.HIGH;
            case CRITICAL -> AiFindingSeverity.CRITICAL;
        };
    }

    private static Map<String, String> attributes(OpsDiagnosisResult result, OpsDiagnosisIssue issue) {
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("source", "AI_HARNESS");
        attributes.put("category", issue.category());
        attributes.put("title", issue.title());
        attributes.put("reviewStatus", result.status().name());
        attributes.put("confidence", result.confidence());
        put(attributes, "likelyCause", issue.likelyCause());
        put(attributes, "recommendation", issue.recommendation());
        put(attributes, "suggestedAction", issue.suggestedAction());
        return attributes;
    }

    private static void put(Map<String, String> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }
}
