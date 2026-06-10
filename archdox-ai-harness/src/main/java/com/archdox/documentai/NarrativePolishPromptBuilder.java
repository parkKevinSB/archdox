package com.archdox.documentai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptBuilder;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class NarrativePolishPromptBuilder implements PromptBuilder<NarrativePolishInput> {
    private final ObjectMapper objectMapper;

    public NarrativePolishPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public RenderedPrompt build(NarrativePolishInput input, AiHarnessRunContext ctx) {
        Objects.requireNonNull(input, "input must not be null");
        var system = """
                You are ArchDox document narrative polish.
                Rewrite short Korean construction supervision report phrases into formal report prose.
                Return JSON only. Do not include markdown.
                The JSON must match:
                {
                  "status": "DRAFTED|NO_CHANGES|NEEDS_HUMAN_REVIEW",
                  "summary": "short Korean summary",
                  "suggestions": [
                    {
                      "path": "input field path",
                      "label": "input field label",
                      "originalText": "input original text",
                      "polishedText": "polished Korean report sentence",
                      "reason": "brief Korean reason",
                      "confidence": "LOW|MEDIUM|HIGH",
                      "applicable": true
                    }
                  ]
                }
                Rules:
                - Preserve facts, quantities, dates, locations, parties, defect status, and inspection meaning.
                - Do not add new facts, legal conclusions, safety certification, or work completion claims.
                - Do not modify the legal corpus or report data. This is a draft suggestion only.
                - If a phrase is too ambiguous to safely rewrite, set applicable=false and explain the reason.
                - If no meaningful rewrite is needed, use status NO_CHANGES and an empty suggestions array.
                - Keep polishedText concise and suitable for a Korean construction supervision daily log.
                """;
        var user = """
                Polish these ArchDox report narrative fields.

                Input JSON:
                %s
                """.formatted(toJson(Map.of(
                "officeId", input.officeId(),
                "reportId", input.reportId(),
                "reportType", input.reportType(),
                "title", input.title(),
                "outputPurpose", input.outputPurpose(),
                "fields", input.fields(),
                "reportContext", input.reportContext())));
        return new RenderedPrompt(List.of(
                new RenderedPrompt.Message(RenderedPrompt.Role.SYSTEM, system),
                new RenderedPrompt.Message(RenderedPrompt.Role.USER, user)), ctx.promptVersion());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to render narrative polish prompt input", ex);
        }
    }
}
