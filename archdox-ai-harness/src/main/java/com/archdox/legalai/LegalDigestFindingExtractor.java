package com.archdox.legalai;

import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFindingSeverity;
import io.github.parkkevinsb.flower.ai.harness.finding.FindingExtractor;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LegalDigestFindingExtractor implements FindingExtractor<LegalDigestResult> {
    @Override
    public List<AiFinding> extract(LegalDigestResult value, AiHarnessRunContext ctx) {
        if (value.status() == LegalDigestStatus.PUBLISHABLE) {
            return List.of();
        }
        var message = value.reviewNotes().isBlank()
                ? "Legal digest needs human review before publishing."
                : value.reviewNotes();
        return List.of(new AiFinding(
                "LEGAL_DIGEST_NEEDS_HUMAN_REVIEW",
                AiFindingSeverity.MEDIUM,
                message,
                value.summary(),
                "LEGAL_DIGEST",
                attributes(value)));
    }

    private static Map<String, String> attributes(LegalDigestResult result) {
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("source", "AI_HARNESS");
        attributes.put("digestStatus", result.status().name());
        attributes.put("confidence", result.confidence());
        if (!result.title().isBlank()) {
            attributes.put("title", result.title());
        }
        return attributes;
    }
}
