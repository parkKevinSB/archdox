package com.archdox.documentai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptBuilder;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ReportPreflightPromptBuilder implements PromptBuilder<ReportPreflightInput> {
    private final ObjectMapper objectMapper;

    public ReportPreflightPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public RenderedPrompt build(ReportPreflightInput input, AiHarnessRunContext ctx) {
        Objects.requireNonNull(input, "input must not be null");
        var system = """
                You are ArchDox report preflight QA.
                Review inspection or construction supervision report input before document generation.
                Return JSON only. Do not include markdown.
                Write summary, message, evidence, and suggestion in Korean for Korean office users.
                Keep code, category, severity, status, confidence, and location as stable English enum-like values.
                The JSON must match:
                {
                  "status": "PASS|WARN|FAIL",
                  "summary": "short Korean review summary",
                  "confidence": "LOW|MEDIUM|HIGH",
                  "issues": [
                    {
                      "code": "stable uppercase code",
                      "category": "GENERAL|COMPLETENESS|CONSISTENCY|EVIDENCE|COMPLIANCE|LEGAL_RISK|WORDING",
                      "severity": "INFO|LOW|MEDIUM|HIGH|CRITICAL",
                      "location": "field, step, checklist item, or section",
                      "message": "human readable Korean issue",
                      "evidence": "Korean explanation of input evidence",
                      "suggestion": "Korean recommended correction"
                    }
                  ]
                }
                Focus on semantic problems that deterministic code checks cannot fully decide:
                inconsistent dates, contradictory answers, vague safety remarks, missing context,
                suspiciously empty narrative fields, and checklist answers that need evidence.
                Also perform a lightweight legal/compliance risk review for the report type.
                This is not legal advice and must not invent laws, article numbers, or facts.
                Use category COMPLIANCE for missing compliance-critical inputs.
                Use category LEGAL_RISK for wording or contradictions that could create audit,
                dispute, or agency-review risk.
                Do not repeat deterministic findings unless you can add a concrete extra reason.
                Use FAIL only for issues that should block document generation.
                Use WARN for issues that should be reviewed by a person before generation.
                Severity guidance:
                - savedAt is the data-entry/save timestamp, not necessarily the real inspection time.
                - Do not mark inspectionDate vs savedAt differences as HIGH or CRITICAL unless the input clearly proves an impossible or legally blocking chronology.
                - If inspectionDate is after savedAt, normally use LOW or MEDIUM and ask the user to verify the date.
                - Use HIGH only when the contradiction directly affects required report facts, approval, or document validity.
                - Use LOW for wording cleanup, typos, or unclear follow-up text that does not block generation.
                - Use MEDIUM for missing evidence, vague safety/compliance remarks, or contradictions that need human review.
                Korean wording guidance:
                - Be concise and professional.
                - Avoid overly legalistic language unless the input includes a concrete compliance risk.
                - Do not translate stable code/category/severity enum values.
                """;
        var user = """
                Review this ArchDox report input before document generation.

                Input JSON:
                %s
                """.formatted(toJson(Map.of(
                "officeId", input.officeId(),
                "reportId", input.reportId(),
                "reportType", input.reportType(),
                "title", input.title(),
                "status", input.status(),
                "contentRevision", input.contentRevision(),
                "reportSnapshot", input.reportSnapshot(),
                "steps", input.steps(),
                "deterministicFindings", input.deterministicFindings(),
                "complianceReviewGuide", ReportComplianceReviewGuide.forReportType(input.reportType()))));
        return new RenderedPrompt(List.of(
                new RenderedPrompt.Message(RenderedPrompt.Role.SYSTEM, system),
                new RenderedPrompt.Message(RenderedPrompt.Role.USER, user)), ctx.promptVersion());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to render report preflight prompt input", ex);
        }
    }
}
