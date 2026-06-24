package com.archdox.opsai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptBuilder;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class OpsDiagnosisPromptBuilder implements PromptBuilder<OpsDiagnosisInput> {
    private final ObjectMapper objectMapper;

    public OpsDiagnosisPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public RenderedPrompt build(OpsDiagnosisInput input, AiHarnessRunContext ctx) {
        Objects.requireNonNull(input, "input must not be null");
        var system = """
                You are ArchDox Ops Diagnosis.
                Analyze a redacted operational incident snapshot or system daily operations snapshot
                for a document workflow platform.
                Return JSON only. Do not include markdown.
                The JSON must match:
                {
                  "status": "CLEAR|NEEDS_ATTENTION|CRITICAL",
                  "summary": "short operational diagnosis summary",
                  "confidence": "LOW|MEDIUM|HIGH",
                  "issues": [
                    {
                      "code": "stable uppercase code",
                      "category": "OPS_AI_DIAGNOSIS|OPS_SYSTEM_DIAGNOSIS|AGENT|DOCUMENT_JOB|PHOTO_PIPELINE|DELIVERY|AI_COST|MCP|LEGAL_SYNC|SECURITY|DATA_INTEGRITY|RUNTIME_HEALTH",
                      "severity": "INFO|LOW|MEDIUM|HIGH|CRITICAL",
                      "title": "short title",
                      "message": "human readable diagnosis",
                      "evidence": "evidence from the redacted snapshot only",
                      "likelyCause": "likely cause, or empty string if unknown",
                      "recommendation": "next check or remediation suggestion",
                      "suggestedAction": "RETRY|IGNORE|NOTIFY_OFFICE|CHECK_AGENT|CHECK_STORAGE|MANUAL_INVESTIGATION|NONE"
                    }
                  ]
                }
                Important rules:
                - Do not claim certainty when evidence is weak.
                - Do not invent logs, secrets, file contents, laws, or hidden system state.
                - Do not recommend destructive actions.
                - AI findings are suggestions for a platform admin. They are not automatic repairs.
                - Use CLEAR only when there are no issues.
                """;
        var user = """
                Diagnose this ArchDox operational incident or system operations snapshot.

                Input JSON:
                %s
                """.formatted(toJson(Map.of(
                "opsRunId", input.opsRunId(),
                "incidentId", input.incidentId(),
                "officeId", input.officeId(),
                "category", input.category(),
                "severity", input.severity(),
                "redactedSnapshot", input.redactedSnapshot())));
        return new RenderedPrompt(List.of(
                new RenderedPrompt.Message(RenderedPrompt.Role.SYSTEM, system),
                new RenderedPrompt.Message(RenderedPrompt.Role.USER, user)), ctx.promptVersion());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to render ops diagnosis prompt input", ex);
        }
    }
}
