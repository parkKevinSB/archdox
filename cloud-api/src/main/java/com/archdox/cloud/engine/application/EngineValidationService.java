package com.archdox.cloud.engine.application;

import com.archdox.cloud.engine.domain.EngineReviewSession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EngineValidationService {
    private static final String CATALOG_BINDING_REVIEW = "CATALOG_BINDING_REVIEW";
    private static final String LEGAL_REFERENCE_BINDING = "LEGAL_REFERENCE_BINDING";
    private static final String LEGAL_RISK_CONTEXT_REVIEW = "LEGAL_RISK_CONTEXT_REVIEW";
    private static final String RETURN_TYPED_RESULT = "RETURN_TYPED_RESULT";

    private final EngineCatalogBindingReviewService catalogBindingReviewService;
    private final EngineLegalReferenceBindingService legalReferenceBindingService;
    private final EngineLegalRiskContextReviewService legalRiskContextReviewService;

    public EngineValidationService(
            EngineCatalogBindingReviewService catalogBindingReviewService,
            EngineLegalReferenceBindingService legalReferenceBindingService,
            EngineLegalRiskContextReviewService legalRiskContextReviewService
    ) {
        this.catalogBindingReviewService = catalogBindingReviewService;
        this.legalReferenceBindingService = legalReferenceBindingService;
        this.legalRiskContextReviewService = legalRiskContextReviewService;
    }

    public EngineValidationResult validate(
            EngineReviewSession session,
            Map<String, Object> normalizedContext
    ) {
        var engineRunId = "eng_" + UUID.randomUUID();
        var findings = new ArrayList<ArchDoxEngineFinding>();
        findings.addAll(deterministicFindings(normalizedContext));

        var catalogReview = catalogBindingReviewService.review(normalizedContext);
        findings.addAll(catalogReview.findings());

        var legalReferenceReview = legalReferenceBindingService.resolve(
                catalogReview.catalogBindings(),
                firstNonBlank(session.documentTypeHint(), session.reviewPurpose()),
                null);
        var legalRiskReview = legalRiskContextReviewService.review(
                normalizedContext,
                catalogReview.catalogBindings(),
                legalReferenceReview.legalReferences());
        findings.addAll(legalRiskReview.findings());

        var status = status(findings);
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("engineBoundaryRole", "RECIPE_AND_CONTEXT_REVIEW");
        metadata.put("governanceBoundary", "ARCHDOX_WORKER_SERVICE");
        metadata.put("workerGovernanceExecuted", false);
        metadata.put("engineRecipeSteps", List.of(
                CATALOG_BINDING_REVIEW,
                LEGAL_REFERENCE_BINDING,
                LEGAL_RISK_CONTEXT_REVIEW,
                RETURN_TYPED_RESULT));
        metadata.put("catalogBindings", catalogReview.catalogBindings());
        metadata.put("catalogReview", catalogReview.metadata());
        metadata.put("legalReferences", legalReferenceReview.legalReferences());
        metadata.put("legalReferenceReview", legalReferenceReview.metadata());
        metadata.put("legalRiskReview", legalRiskReview.metadata());
        metadata.put("suggestedWorkerActions", List.of());

        return new EngineValidationResult(
                engineRunId,
                status,
                status == ArchDoxEngineResultStatus.PASS,
                findings.isEmpty()
                        ? "Context is ready for the current engine recipe review."
                        : "Context needs review before high-confidence validation.",
                List.copyOf(findings),
                legalReferenceReview.legalReferences(),
                nextActions(findings),
                "NOT_APPLICABLE_ENGINE_RECIPE_ONLY",
                List.of(
                        CATALOG_BINDING_REVIEW,
                        LEGAL_REFERENCE_BINDING,
                        LEGAL_RISK_CONTEXT_REVIEW,
                        RETURN_TYPED_RESULT),
                "ENGINE_RECIPE_VALIDATION",
                Map.copyOf(metadata));
    }

    private List<ArchDoxEngineFinding> deterministicFindings(Map<String, Object> normalizedContext) {
        var findings = new ArrayList<ArchDoxEngineFinding>();
        listOfMaps(normalizedContext.get("missingQuestions")).forEach(question -> findings.add(new ArchDoxEngineFinding(
                "MISSING_CONTEXT",
                "COMPLETENESS",
                "MEDIUM",
                ArchDoxEngineFindingSource.DETERMINISTIC,
                text(question.get("fieldName")),
                text(question.get("question")),
                List.of(),
                Map.of("engineCheck", "NORMALIZED_CONTEXT_REQUIRED_FIELD"))));
        listOfMaps(normalizedContext.get("ambiguities")).forEach(ambiguity -> findings.add(new ArchDoxEngineFinding(
                "AMBIGUOUS_CONTEXT",
                "CONSISTENCY",
                "LOW",
                ArchDoxEngineFindingSource.DETERMINISTIC,
                text(ambiguity.get("fieldName")),
                text(ambiguity.get("question")),
                List.of(),
                Map.of(
                        "engineCheck", "NORMALIZED_CONTEXT_AMBIGUITY",
                        "rawValue", text(ambiguity.get("rawValue"))))));
        return List.copyOf(findings);
    }

    private List<String> nextActions(List<ArchDoxEngineFinding> findings) {
        if (findings.isEmpty()) {
            return List.of("RESULT_READY");
        }
        var actions = new ArrayList<String>();
        if (findings.stream().anyMatch(finding -> finding.code().startsWith("CATALOG_SELECTION"))) {
            actions.add("FIX_CATALOG_SELECTION");
        }
        if (findings.stream().anyMatch(finding -> "MISSING_CONTEXT".equals(finding.code()))) {
            actions.add("ANSWER_MISSING_CONTEXT");
        }
        if (findings.stream().anyMatch(finding -> "AMBIGUOUS_CONTEXT".equals(finding.code()))) {
            actions.add("RESOLVE_AMBIGUITY");
        }
        if (findings.stream().anyMatch(finding -> "LEGAL_EVIDENCE_CONTEXT_MISSING".equals(finding.code())
                || "LEGAL_TECHNICAL_EVIDENCE_CONTEXT_LIMITED".equals(finding.code()))) {
            actions.add("ADD_SUPERVISION_EVIDENCE_CONTEXT");
        }
        actions.add("RUN_VALIDATION_AGAIN");
        return List.copyOf(actions);
    }

    private ArchDoxEngineResultStatus status(List<ArchDoxEngineFinding> findings) {
        if (findings.isEmpty()) {
            return ArchDoxEngineResultStatus.PASS;
        }
        if (findings.stream().anyMatch(finding -> "HIGH".equals(finding.severity())
                || "CRITICAL".equals(finding.severity()))) {
            return ArchDoxEngineResultStatus.FAIL;
        }
        return ArchDoxEngineResultStatus.WARN;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        if (value instanceof List<?> items) {
            return items.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
