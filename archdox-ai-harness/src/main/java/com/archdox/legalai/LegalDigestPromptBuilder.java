package com.archdox.legalai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptBuilder;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

public final class LegalDigestPromptBuilder implements PromptBuilder<LegalDigestInput> {
    private final ObjectMapper objectMapper;

    public LegalDigestPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public RenderedPrompt build(LegalDigestInput input, AiHarnessRunContext ctx) {
        Objects.requireNonNull(input, "input must not be null");
        var system = """
                You are ArchDox Legal Change Digest Writer.
                Turn source-backed legal change records into a short user-facing update for Korean construction supervision users.
                Return JSON only. Do not include markdown.

                The JSON must match:
                {
                  "status": "PUBLISHABLE|NEEDS_HUMAN_REVIEW",
                  "title": "short Korean title",
                  "summary": "Korean summary of what changed",
                  "impactSummary": "Korean explanation of likely ArchDox 업무 impact",
                  "confidence": "LOW|MEDIUM|HIGH",
                  "affectedReportTypes": ["stable ArchDox report type codes"],
                  "affectedCatalogItems": ["stable ArchDox catalog item codes"],
                  "keyArticles": ["article keys or form/table identifiers from input only"],
                  "reviewNotes": "short Korean note for admin reviewers"
                }

                Source rules:
                - Use only legal text and metadata in the input JSON.
                - Do not invent law names, article numbers, version ids, effective dates, citations, URLs, or external facts.
                - If the input is too fragmented to summarize safely, return NEEDS_HUMAN_REVIEW.
                - This is not legal advice. Describe operational impact and review needs, not final legal conclusions.

                Writing rules:
                - Write title, summary, impactSummary, and reviewNotes in Korean.
                - Keep stable code values in English, such as CONSTRUCTION_DAILY_SUPERVISION_LOG.
                - Prefer concise, board-post style wording for ordinary users.
                - For tables, forms, attachments, or long OCR-like text, summarize the changed structure and fields.
                - Do not paste long raw table/form blocks into the summary.
                - Mention key articles or form/table identifiers only when they appear in input.
                """;
        var user = """
                Draft a legal change digest from this source-backed ArchDox legal corpus change set.

                Input JSON:
                %s
                """.formatted(toJson(promptInput(input)));
        return new RenderedPrompt(List.of(
                new RenderedPrompt.Message(RenderedPrompt.Role.SYSTEM, system),
                new RenderedPrompt.Message(RenderedPrompt.Role.USER, user)), ctx.promptVersion());
    }

    private LinkedHashMap<String, Object> promptInput(LegalDigestInput input) {
        var value = new LinkedHashMap<String, Object>();
        value.put("changeSetId", input.changeSetId());
        value.put("actCode", input.actCode());
        value.put("actName", input.actName());
        value.put("actType", input.actType());
        value.put("sourceCode", input.sourceCode());
        value.put("effectiveDate", input.effectiveDate());
        value.put("detectedAt", input.detectedAt());
        value.put("sourceSummary", input.sourceSummary());
        value.put("articleChanges", input.articleChanges());
        value.put("archdoxBusinessScope", List.of(
                "construction supervision MVP",
                "construction daily supervision log",
                "construction supervision report",
                "construction supervision checklist and legal context catalog"));
        return value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to render legal digest prompt input", ex);
        }
    }
}
