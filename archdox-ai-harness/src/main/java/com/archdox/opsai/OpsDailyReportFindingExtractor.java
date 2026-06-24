package com.archdox.opsai;

import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFindingSeverity;
import io.github.parkkevinsb.flower.ai.harness.finding.FindingExtractor;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OpsDailyReportFindingExtractor implements FindingExtractor<OpsDailyReportResult> {
    @Override
    public List<AiFinding> extract(OpsDailyReportResult value, AiHarnessRunContext ctx) {
        var findings = new ArrayList<AiFinding>();
        findings.add(new AiFinding(
                "OPS_DAILY_REPORT_AI_SUMMARY",
                summarySeverity(value.status()),
                value.summary(),
                "AI summarized the provided redacted operations evidence.",
                "OPS_DAILY_REPORT",
                summaryAttributes(value)));
        value.issues().stream()
                .map(issue -> new AiFinding(
                        issue.code(),
                        toFindingSeverity(issue.severity()),
                        issue.message(),
                        issue.evidence(),
                        issue.category(),
                        issueAttributes(value, issue)))
                .forEach(findings::add);
        return findings;
    }

    private static AiFindingSeverity summarySeverity(OpsDailyReportStatus status) {
        return switch (status) {
            case CLEAR -> AiFindingSeverity.INFO;
            case WATCH -> AiFindingSeverity.LOW;
            case ACTION_REQUIRED -> AiFindingSeverity.MEDIUM;
            case CRITICAL -> AiFindingSeverity.CRITICAL;
        };
    }

    private static AiFindingSeverity toFindingSeverity(OpsDailyReportIssueSeverity severity) {
        return switch (severity) {
            case INFO -> AiFindingSeverity.INFO;
            case LOW -> AiFindingSeverity.LOW;
            case MEDIUM -> AiFindingSeverity.MEDIUM;
            case HIGH -> AiFindingSeverity.HIGH;
            case CRITICAL -> AiFindingSeverity.CRITICAL;
        };
    }

    private static Map<String, String> summaryAttributes(OpsDailyReportResult result) {
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("source", "AI_HARNESS");
        attributes.put("category", "OPS_DAILY_REPORT");
        attributes.put("title", "AI operations daily report summary");
        attributes.put("reviewStatus", result.status().name());
        attributes.put("confidence", result.confidence());
        put(attributes, "pLikeCurrentFindings", join(result.pLikeCurrentFindings()));
        put(attributes, "iLikeAccumulatedSignals", join(result.iLikeAccumulatedSignals()));
        put(attributes, "dLikeTrendSignals", join(result.dLikeTrendSignals()));
        put(attributes, "recommendations", join(result.recommendations()));
        return attributes;
    }

    private static Map<String, String> issueAttributes(OpsDailyReportResult result, OpsDailyReportIssue issue) {
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

    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join("\n", values);
    }

    private static void put(Map<String, String> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }
}
