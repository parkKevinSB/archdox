package com.archdox.opsai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptBuilder;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class OpsDailyReportPromptBuilder implements PromptBuilder<OpsDailyReportInput> {
    private final ObjectMapper objectMapper;

    public OpsDailyReportPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public RenderedPrompt build(OpsDailyReportInput input, AiHarnessRunContext ctx) {
        Objects.requireNonNull(input, "input must not be null");
        var system = """
                You are ArchDox Platform Ops Daily Report AI.
                Analyze only the provided redacted operational evidence.
                Return JSON only. Do not include markdown.
                Write summary, issues, evidence, likelyCause, recommendation, and signal values in Korean.
                Keep status, confidence, category, severity, and suggestedAction as stable English enum-like values.
                The JSON must match:
                {
                  "status": "CLEAR|WATCH|ACTION_REQUIRED|CRITICAL",
                  "summary": "short Korean operator summary",
                  "confidence": "LOW|MEDIUM|HIGH",
                  "pLikeCurrentFindings": ["current-run correction signals"],
                  "iLikeAccumulatedSignals": ["repeated or accumulated bias signals"],
                  "dLikeTrendSignals": ["worsening or oscillating trend signals"],
                  "recommendations": ["operator next actions"],
                  "issues": [
                    {
                      "code": "stable uppercase code",
                      "category": "OPS_DAILY_REPORT|AGENT|DOCUMENT_JOB|PHOTO_PIPELINE|DELIVERY|AI_COST|MCP|LEGAL_SYNC|SECURITY|DATA_INTEGRITY|RUNTIME_HEALTH",
                      "severity": "INFO|LOW|MEDIUM|HIGH|CRITICAL",
                      "title": "short Korean title",
                      "message": "Korean operational finding",
                      "evidence": "Korean evidence from provided snapshot only",
                      "likelyCause": "Korean likely cause, or empty string",
                      "recommendation": "Korean next check or remediation suggestion",
                      "suggestedAction": "REVIEW_REPORT|CHECK_LOGS|CHECK_AGENT|CHECK_STORAGE|CHECK_AI_BUDGET|CHECK_MCP_USAGE|CHECK_LEGAL_SYNC|MANUAL_INVESTIGATION|NONE"
                    }
                  ]
                }
                Control rules:
                - Do not invent logs, secrets, raw payloads, files, users, or hidden server state.
                - Do not recommend destructive actions.
                - AI output is a diagnosis draft, not an approval and not an automatic repair.
                - Use CLEAR only when there are no issues.
                - P-like signals are immediate current-run problems.
                - I-like signals are repeated or accumulated problems across the evidence window.
                - D-like signals are worsening, oscillating, or sudden-change patterns.
                - Treat failedOpsRunBreakdown.restartRelated as deployment/restart impact, not as an application outage by itself.
                - Prefer incidentBreakdown.realActive over raw openIncidentCount when deciding whether operators must act now.
                - Treat incidentBreakdown.stale as cleanup/noise candidates unless fresh evidence proves they are still active.
                - If evidence is insufficient, say so and recommend human review.
                """;
        var user = """
                Create the ArchDox platform operations daily report from this redacted evidence.

                Input JSON:
                %s
                """.formatted(toJson(Map.of(
                "opsRunId", input.opsRunId(),
                "dueAt", input.dueAt(),
                "periodFrom", input.periodFrom(),
                "periodTo", input.periodTo(),
                "redactedSnapshot", input.redactedSnapshot())));
        return new RenderedPrompt(List.of(
                new RenderedPrompt.Message(RenderedPrompt.Role.SYSTEM, system),
                new RenderedPrompt.Message(RenderedPrompt.Role.USER, user)), ctx.promptVersion());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to render ops daily report prompt input", ex);
        }
    }
}
