package com.archdox.cloud.engine.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EngineLegalRiskContextReviewService {
    private static final List<String> EVIDENCE_CONTEXT_FIELDS = List.of(
            "supervisionContent",
            "evidenceText",
            "photoEvidence",
            "photoIds",
            "workArea",
            "floor");

    public EngineLegalRiskContextReviewResult review(
            Map<String, Object> normalizedContext,
            List<Map<String, Object>> catalogBindings,
            List<Map<String, Object>> legalReferences
    ) {
        var references = legalReferences == null ? List.<Map<String, Object>>of() : legalReferences;
        var hasLegalReferences = !references.isEmpty();
        var evidenceContext = evidenceContext(normalizedContext);
        var findings = new ArrayList<ArchDoxEngineFinding>();
        if (hasLegalReferences && evidenceContext.isEmpty()) {
            findings.add(new ArchDoxEngineFinding(
                    "LEGAL_EVIDENCE_CONTEXT_MISSING",
                    "LEGAL_RISK",
                    "MEDIUM",
                    ArchDoxEngineFindingSource.LEGAL_CORPUS,
                    "context.evidence",
                    "Legal/domain references are attached to this supervision item, but the review context has no supervision narrative, work area, photo, or evidence field.",
                    referenceIds(references),
                    Map.of(
                            "engineCheck", "LEGAL_REFERENCE_CONTEXT_REQUIRED",
                            "legalReferenceCount", references.size(),
                            "recommendedFields", EVIDENCE_CONTEXT_FIELDS)));
        }

        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("legalRiskContextReviewApplied", hasLegalReferences);
        metadata.put("legalReferenceCount", references.size());
        metadata.put("evidenceContextPresent", !evidenceContext.isEmpty());
        metadata.put("evidenceContextFields", evidenceContext.keySet().stream().toList());
        metadata.put("aiPromptContext", aiPromptContext(catalogBindings, references, evidenceContext));

        return new EngineLegalRiskContextReviewResult(findings, Map.copyOf(metadata));
    }

    private Map<String, Object> aiPromptContext(
            List<Map<String, Object>> catalogBindings,
            List<Map<String, Object>> legalReferences,
            Map<String, Object> evidenceContext
    ) {
        var context = new LinkedHashMap<String, Object>();
        context.put("purpose", "SOURCE_BACKED_LEGAL_RISK_REVIEW_CONTEXT");
        context.put("domainCatalogBindings", catalogBindings == null ? List.of() : catalogBindings);
        context.put("legalReferences", legalReferences == null ? List.of() : compactReferences(legalReferences));
        context.put("evidenceContext", evidenceContext);
        context.put("instructions", List.of(
                "Use only the supplied legalReferences as legal/source anchors.",
                "Do not invent laws, article numbers, dates, or source titles.",
                "If evidenceContext is empty or weak, ask for field evidence instead of concluding noncompliance.",
                "Return structured findings that distinguish deterministic gaps from model judgment."));
        return Map.copyOf(context);
    }

    private List<Map<String, Object>> compactReferences(List<Map<String, Object>> legalReferences) {
        return legalReferences.stream()
                .map(reference -> {
                    var compact = new LinkedHashMap<String, Object>();
                    compact.put("referenceId", text(reference.get("referenceId")));
                    compact.put("actCode", text(reference.get("actCode")));
                    compact.put("actName", text(reference.get("actName")));
                    compact.put("articleNo", text(reference.get("articleNo")));
                    compact.put("articleTitle", text(reference.get("articleTitle")));
                    compact.put("sourceVersionKey", text(reference.get("sourceVersionKey")));
                    compact.put("relevance", text(reference.get("relevance")));
                    compact.put("notes", text(reference.get("notes")));
                    return Map.copyOf(compact);
                })
                .toList();
    }

    private List<String> referenceIds(List<Map<String, Object>> legalReferences) {
        return legalReferences.stream()
                .map(reference -> firstNonBlank(
                        text(reference.get("referenceId")),
                        text(reference.get("actCode")) + ":" + text(reference.get("articleNo"))))
                .filter(value -> !value.isBlank() && !":".equals(value))
                .distinct()
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> evidenceContext(Map<String, Object> normalizedContext) {
        if (normalizedContext == null) {
            return Map.of();
        }
        var values = normalizedContext.get("values");
        if (!(values instanceof Map<?, ?> rawValues)) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Object>();
        for (var fieldName : EVIDENCE_CONTEXT_FIELDS) {
            var rawValue = rawValues.get(fieldName);
            if (!(rawValue instanceof Map<?, ?> rawMap)) {
                continue;
            }
            var valueMap = (Map<String, Object>) rawMap;
            var value = firstNonBlank(text(valueMap.get("canonicalValue")), text(valueMap.get("rawValue")));
            if (!value.isBlank()) {
                result.put(fieldName, value);
            }
        }
        return Map.copyOf(result);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record EngineLegalRiskContextReviewResult(
            List<ArchDoxEngineFinding> findings,
            Map<String, Object> metadata
    ) {
        public EngineLegalRiskContextReviewResult {
            findings = findings == null ? List.of() : List.copyOf(findings);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
