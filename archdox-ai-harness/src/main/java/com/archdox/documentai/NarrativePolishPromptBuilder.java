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
                Rewrite Korean construction supervision report phrases into polished formal report prose for document generation.
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
                - This is a generation polish task, not only typo correction. Terse field notes should normally be rewritten into complete report sentences.
                - Convert short expressions such as "이상 없음", "지적사항 없음", "다음 조치 없음", "확인함", and "확인시 이상 없음" into natural formal report prose.
                - Return a suggestion for every field that can be improved in formality, clarity, spacing, particles, or sentence completion without changing facts.
                - Use NO_CHANGES only when the input is already a complete, natural, formal report sentence and rewriting would be merely stylistic churn.
                - If a phrase is too ambiguous to safely rewrite, keep applicable=false and explain the reason.
                - Keep polishedText concise and suitable for a Korean construction supervision daily log.
                - Avoid ceremonial or self-reporting endings such as "보고합니다", "보고드립니다", or "기록하였음을 보고합니다".
                - Prefer direct daily-log statements such as "지적사항이 없습니다.", "특기사항이 없습니다.", "추가 조치 사항이 없습니다.", and "확인하였습니다."
                - Do not expand simple no-issue fields into verbose sentences like "본 현장 점검 결과, 별도의 지적사항이 없었음을 보고합니다."
                - For remarks fields, "특기사항 없이 좋음", "특기사항 없음", or similar no-issue phrases should become "특기사항이 없습니다."
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
