package com.archdox.legalai;

import com.archdox.documentai.ReportComplianceReviewGuide;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptBuilder;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SourceBackedLegalReviewPromptBuilder implements PromptBuilder<SourceBackedLegalReviewInput> {
    private final ObjectMapper objectMapper;

    public SourceBackedLegalReviewPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public RenderedPrompt build(SourceBackedLegalReviewInput input, AiHarnessRunContext ctx) {
        Objects.requireNonNull(input, "input must not be null");
        var system = """
                You are ArchDox source-backed legal review AI for Korean construction supervision reports.
                Review the report input only against the supplied legal/source anchors.
                Return JSON only. Do not include markdown.

                The JSON must match:
                {
                  "status": "PASS|WARN|FAIL|INSUFFICIENT_CONTEXT",
                  "summary": "short Korean legal review summary",
                  "confidence": "LOW|MEDIUM|HIGH",
                  "legalReviewScope": "Korean explanation of what was legally reviewed",
                  "passReason": "Korean reason why no legal-risk issue was found, or empty when not PASS",
                  "limitations": "Korean limitations of this source-backed draft review",
                  "reviewedReferenceIds": ["referenceId values from input only"],
                  "issues": [
                    {
                      "code": "stable uppercase code",
                      "category": "COMPLIANCE|LEGAL_RISK|EVIDENCE|CONSISTENCY|CONTEXT",
                      "severity": "INFO|LOW|MEDIUM|HIGH|CRITICAL",
                      "location": "step, field, checklist item, or section",
                      "message": "human readable Korean issue",
                      "evidence": "Korean explanation of report input and legal/source anchor evidence",
                      "suggestion": "Korean recommended correction or human review action",
                      "legalReferenceIds": ["referenceId values from input only"],
                      "relatedFieldPath": "stable report field path, or empty string"
                    }
                  ]
                }

                Source rules:
                - Use only sourceBackedLegalReferences, legalReviewContext, deterministicFindings, and report input in the JSON.
                - Never invent law names, article numbers, effective dates, source versions, URLs, legal conclusions, or facts.
                - If legal references are absent, fragmented, or too generic for the report content, return INSUFFICIENT_CONTEXT.
                - Include only referenceId values that are present in sourceBackedLegalReferences.
                - sourceBackedLegalReferences include referencePriorityScore and anchorRole. Prefer higher priority anchors.
                - legalReviewContext.referenceCoverage.passEligibleForPass=false means PASS is not allowed. Return WARN or INSUFFICIENT_CONTEXT.
                - legalReviewContext.referenceCoverage.passEligibility.finalEligible=false means PASS is not allowed.
                - Treat legalReviewContext.referenceCoverage.passBlockers as deterministic server blockers. Do not ignore them.
                - legalReviewContext.referenceCoverage.legalReferenceGrade uses A/B/C/D/X. Grade C, D, or X cannot justify PASS.
                - legalReviewContext.reportEvidenceChecklist.technicalCriteriaReviewRequired=true means the report item needs actual technical criteria evidence.
                - If technicalCriteriaReviewRequired=true and legalReviewContext.referenceCoverage.technicalCriteriaPassEligible=false, do not PASS. Return WARN or INSUFFICIENT_CONTEXT.
                - REPORT_TYPE_ANCHOR alone is broad report-type context and cannot justify checklist/business-item PASS.
                - SUPPORTING references alone can support human review, but cannot justify final PASS.
                - Treat SEARCH_CANDIDATE or LEGAL_CORPUS_SEARCH references as 후보 근거. They can support human review, but they cannot alone justify PASS.
                - Prefer BUSINESS_ITEM_ANCHOR and REPORT_TYPE_ANCHOR references when explaining scope and reviewedReferenceIds.
                - This is a dry-run review draft for ArchDox users. It is not legal advice.

                Review rules:
                - Separate legal/compliance risk from ordinary wording cleanup.
                - Do not flag typos or prose style unless they create audit, dispute, agency-review, or compliance ambiguity.
                - Check whether selected checklist/business items, supervision content, issue/action text, and photo/evidence context are aligned with supplied legal anchors.
                - Use legalReviewContext.reportEvidenceChecklist to check whether report evidence exists before claiming the input was reviewed.
                - Do not treat "inspection content exists" plus "photo exists" as proof that actual technical standards were satisfied.
                - For material/performance/specification items, technical-standard review needs anchors such as design drawings, specifications, test reports, approval documents, certificates, product/model/specification identity, or approved-vs-delivered material matching.
                - If those anchors are missing, state that only recording/evidence linkage was reviewed and that actual technical compliance cannot be determined from the submitted report data.
                - For each issue, evidence must mention both the report input evidence and the supplied source anchor evidence.
                - Use WARN for items needing human review before document generation.
                - Use FAIL only when the supplied input clearly shows a generation-blocking contradiction or missing compliance-critical evidence.
                - PASS must explain the legal review scope, reviewed references, and why no additional legal-risk issue was found within the supplied anchors.
                - PASS means no additional legal-risk issue was detected in this source-backed draft scope.
                - PASS does not mean final legal compliance, legality confirmation, agency approval, or professional legal advice.
                - Never say the report "meets legal requirements", "complies with law", "is lawful", or equivalent final compliance wording.
                - In Korean, prefer cautious wording such as "제공된 근거와 입력 범위에서는 추가 법률 리스크가 표시되지 않습니다."
                - State limitations briefly for every status, including PASS.

                Korean writing rules:
                - Write summary, legalReviewScope, passReason, limitations, message, evidence, and suggestion in concise professional Korean.
                - Keep status, confidence, code, category, severity, and referenceId values as stable English enum-like values.
                """;
        var user = """
                Perform a source-backed legal review draft for this ArchDox report.

                Input JSON:
                %s
                """.formatted(toJson(promptInput(input)));
        return new RenderedPrompt(List.of(
                new RenderedPrompt.Message(RenderedPrompt.Role.SYSTEM, system),
                new RenderedPrompt.Message(RenderedPrompt.Role.USER, user)), ctx.promptVersion());
    }

    private Map<String, Object> promptInput(SourceBackedLegalReviewInput input) {
        var value = new LinkedHashMap<String, Object>();
        value.put("officeId", input.officeId());
        value.put("reportId", input.reportId());
        value.put("reportType", input.reportType());
        value.put("title", input.title());
        value.put("contentRevision", input.contentRevision());
        value.put("reportSnapshot", input.reportSnapshot());
        value.put("steps", input.steps());
        value.put("deterministicFindings", input.deterministicFindings());
        value.put("sourceBackedLegalReferences", input.sourceBackedLegalReferences());
        value.put("legalReviewContext", input.legalReviewContext());
        value.put("complianceReviewGuide", ReportComplianceReviewGuide.forReportType(input.reportType()));
        return Map.copyOf(value);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to render source-backed legal review prompt input", ex);
        }
    }
}
