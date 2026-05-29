package com.archdox.documentai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptBuilder;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DocumentQaPromptBuilder implements PromptBuilder<DocumentQaInput> {
    private final ObjectMapper objectMapper;

    public DocumentQaPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public RenderedPrompt build(DocumentQaInput input, AiHarnessRunContext ctx) {
        Objects.requireNonNull(input, "input must not be null");
        var system = """
                You are ArchDox document QA.
                Review a generated construction or inspection document package.
                Return JSON only. Do not include markdown.
                The JSON must match:
                {
                  "status": "PASS|WARN|FAIL",
                  "summary": "short review summary",
                  "confidence": "LOW|MEDIUM|HIGH",
                  "issues": [
                    {
                      "code": "stable uppercase code",
                      "severity": "INFO|LOW|MEDIUM|HIGH|CRITICAL",
                      "location": "field, section, or artifact",
                      "message": "human readable issue",
                      "evidence": "input evidence",
                      "suggestion": "recommended correction"
                    }
                  ]
                }
                Use PASS only when there are no issues. Use FAIL for missing mandatory identity,
                report, template, or evidence information. Use WARN for non-blocking quality issues.
                """;
        var user = """
                Review this ArchDox document package.

                Input JSON:
                %s
                """.formatted(toJson(Map.of(
                "officeId", input.officeId(),
                "documentJobId", input.documentJobId(),
                "reportId", input.reportId(),
                "reportType", input.reportType(),
                "title", input.title(),
                "outputFormat", input.outputFormat(),
                "artifacts", input.artifacts(),
                "renderedText", input.renderedText(),
                "reportSnapshot", input.reportSnapshot())));
        return new RenderedPrompt(List.of(
                new RenderedPrompt.Message(RenderedPrompt.Role.SYSTEM, system),
                new RenderedPrompt.Message(RenderedPrompt.Role.USER, user)), ctx.promptVersion());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to render document QA prompt input", ex);
        }
    }
}
