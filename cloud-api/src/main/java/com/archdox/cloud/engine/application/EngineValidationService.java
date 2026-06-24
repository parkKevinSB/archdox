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
    private static final String DOCUMENT_QUALITY_REVIEW = "DOCUMENT_QUALITY_REVIEW";
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
        findings.addAll(documentQualityFindings(session, normalizedContext));

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
                DOCUMENT_QUALITY_REVIEW,
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
                        DOCUMENT_QUALITY_REVIEW,
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

    private List<ArchDoxEngineFinding> documentQualityFindings(
            EngineReviewSession session,
            Map<String, Object> normalizedContext
    ) {
        if (!isConstructionDailySupervisionLog(session, normalizedContext)) {
            return List.of();
        }
        if (!hasDailyLogDocumentShape(session, normalizedContext)) {
            return List.of();
        }
        var findings = new ArrayList<ArchDoxEngineFinding>();
        var values = values(normalizedContext);
        var documentText = firstNonBlank(
                value(values, "selectedDocumentText"),
                text(session.documentText()));
        var internalReportPreflight = isArchDoxSaasReportPreflight(normalizedContext);
        var supervisionContent = value(values, "supervisionContent");
        var specialNotes = firstNonBlank(
                value(values, "specialNotes"),
                value(values, "remarks"));
        var issueAndAction = value(values, "issueAndAction");

        if (isBlank(specialNotes) && (internalReportPreflight
                || sectionEmpty(documentText, "특기사항", "지적사항 및 처리결과"))) {
            findings.add(documentQualityFinding(
                    "DAILY_LOG_SPECIAL_NOTES_EMPTY",
                    "LOW",
                    "steps.REMARKS.payload.specialNotes",
                    "특기사항이 비어 있습니다. 특별한 사항이 없었다면 '특기사항 없음'처럼 명시하는 편이 문서 완성도가 높습니다.",
                    Map.of(
                            "recommendedText", "특기사항 없음.",
                            "qualityCheck", "DAILY_LOG_EMPTY_SECTION")));
        }
        if (isBlank(issueAndAction) && (internalReportPreflight
                || sectionEmpty(documentText, "지적사항 및 처리결과", "작성방법"))) {
            findings.add(documentQualityFinding(
                    "DAILY_LOG_ISSUE_AND_ACTION_EMPTY",
                    "LOW",
                    "steps.REMARKS.payload.issueAndAction",
                    "지적사항 및 처리결과가 비어 있습니다. 지적사항이 없었다면 '지적사항 및 처리결과 없음'처럼 명시하는 편이 좋습니다.",
                    Map.of(
                            "recommendedText", "지적사항 및 처리결과 없음.",
                            "qualityCheck", "DAILY_LOG_EMPTY_SECTION")));
        }
        if (looksLikeChecklistOnly(supervisionContent, normalizedContext)) {
            findings.add(documentQualityFinding(
                    "DAILY_LOG_SUPERVISION_RESULT_WORDING_WEAK",
                    "LOW",
                    "context.supervisionContent",
                    "감리내용이 체크리스트 항목 나열에 가깝습니다. 확인 결과와 업무수행 근거가 드러나도록 문장을 보완하는 것을 권장합니다.",
                    Map.of(
                            "recommendedText", "검사항목별 확인 결과 이상 없으며, 관련 기준 및 설계도서와 대조 확인함.",
                            "qualityCheck", "DAILY_LOG_RESULT_WORDING")));
        }
        if (hasGenericTechnicalDocumentReference(supervisionContent)
                && !hasSpecificTechnicalDocumentTrace(supervisionContent)) {
            findings.add(documentQualityFinding(
                    "DAILY_LOG_TECHNICAL_DOCUMENT_TRACE_WEAK",
                    "MEDIUM",
                    "context.supervisionContent",
                    "자재성능 관련 서류 확인 표현이 감리일지 문장으로 충분히 구체화되지 않았습니다. 필요한 서류 확인·첨부 문구가 드러나도록 감리내용을 보완하세요.",
                    Map.of(
                            "recommendedText", "KS 등 자재성능 관련 서류를 확인하였으며, 시험성적서 및 자재승인서 등 관련 서류를 확인하고 첨부하였습니다.",
                            "qualityCheck", "DAILY_LOG_TECHNICAL_TRACE")));
        }
        if (isExternalDocumentReview(normalizedContext)
                && isBlank(value(values, "photoEvidence"))
                && isBlank(value(values, "photoIds"))) {
            findings.add(documentQualityFinding(
                    "DAILY_LOG_PHOTO_EVIDENCE_NOT_SUPPLIED",
                    "LOW",
                    "context.photoEvidence",
                    "PDF/MCP 입력만으로는 현장 사진 근거 연결 여부를 확인할 수 없습니다. 사진이 별도 보관 또는 ArchDox 리포트에 연결되어 있다면 문제는 아니지만, 제출 전 연결 상태를 확인하세요.",
                    Map.of(
                            "recommendedText", "관련 현장 사진은 별도 보관 또는 리포트 사진 증거로 연결함.",
                            "qualityCheck", "DAILY_LOG_PHOTO_EVIDENCE")));
        }
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
        if (findings.stream().anyMatch(finding -> finding.code().startsWith("DAILY_LOG_"))) {
            actions.add("IMPROVE_DAILY_LOG_DOCUMENT_QUALITY");
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

    private ArchDoxEngineFinding documentQualityFinding(
            String code,
            String severity,
            String location,
            String message,
            Map<String, Object> metadata
    ) {
        var details = new LinkedHashMap<String, Object>(metadata == null ? Map.of() : metadata);
        details.put("engineCheck", DOCUMENT_QUALITY_REVIEW);
        details.put("reportType", "CONSTRUCTION_DAILY_SUPERVISION_LOG");
        return new ArchDoxEngineFinding(
                code,
                "COMPLETENESS",
                severity,
                ArchDoxEngineFindingSource.DETERMINISTIC,
                location,
                message,
                List.of(),
                Map.copyOf(details));
    }

    private boolean isConstructionDailySupervisionLog(
            EngineReviewSession session,
            Map<String, Object> normalizedContext
    ) {
        var reportType = firstNonBlank(
                value(values(normalizedContext), "reportType"),
                firstNonBlank(session.documentTypeHint(), session.reviewPurpose()));
        return "CONSTRUCTION_DAILY_SUPERVISION_LOG".equalsIgnoreCase(reportType);
    }

    private boolean hasDailyLogDocumentShape(
            EngineReviewSession session,
            Map<String, Object> normalizedContext
    ) {
        var source = text(normalizedContext.get("source"));
        if ("ARCHDOX_SAAS_REPORT_PREFLIGHT".equals(source)) {
            return true;
        }
        var contextValues = values(normalizedContext);
        var documentText = normalizeKoreanText(firstNonBlank(
                value(contextValues, "selectedDocumentText"),
                text(session.documentText())));
        return documentText.contains("공사감리일지") || documentText.contains("감리일지");
    }

    private boolean isExternalDocumentReview(Map<String, Object> normalizedContext) {
        return !isArchDoxSaasReportPreflight(normalizedContext);
    }

    private boolean isArchDoxSaasReportPreflight(Map<String, Object> normalizedContext) {
        return "ARCHDOX_SAAS_REPORT_PREFLIGHT".equals(text(normalizedContext.get("source")));
    }

    private boolean sectionEmpty(String documentText, String startMarker, String endMarker) {
        var text = documentText == null ? "" : documentText;
        var start = text.indexOf(startMarker);
        if (start < 0) {
            return false;
        }
        var contentStart = start + startMarker.length();
        var end = endMarker == null || endMarker.isBlank() ? -1 : text.indexOf(endMarker, contentStart);
        if (end < 0) {
            end = text.length();
        }
        var section = text.substring(contentStart, end)
                .replaceAll("[\\s:：ㆍ·\\-–—_()\\[\\]]+", "")
                .trim();
        return section.isBlank();
    }

    private boolean looksLikeChecklistOnly(String supervisionContent, Map<String, Object> normalizedContext) {
        var content = text(supervisionContent);
        if (content.isBlank()) {
            return false;
        }
        var catalogSelectionCount = listOfMaps(normalizedContext.get("catalogSelections")).size();
        if (catalogSelectionCount < 3) {
            return false;
        }
        var lower = content.toLowerCase(java.util.Locale.ROOT);
        var hasResultWording = containsAny(lower,
                "이상 없음",
                "적합",
                "부적합",
                "보완",
                "재시공",
                "조치",
                "대조 확인",
                "별도 보관",
                "사진",
                "첨부",
                "approved",
                "not approved",
                "nonconforming");
        if (hasResultWording) {
            return false;
        }
        var confirmationCount = countContains(content, "확인");
        return confirmationCount >= 3;
    }

    private boolean hasGenericTechnicalDocumentReference(String content) {
        var value = text(content);
        return containsAny(value,
                "자재성능 관련 서류 확인",
                "KS등 자재성능 관련 서류 확인",
                "KS 등 자재성능 관련 서류 확인",
                "규격 증명서",
                "시험성적증명서",
                "성능 관련 서류");
    }

    private boolean hasSpecificTechnicalDocumentTrace(String content) {
        var value = text(content);
        return containsAny(value,
                "별도 보관",
                "대조 확인",
                "첨부",
                "첨부하였",
                "시험성적서",
                "자재승인서",
                "인증서",
                "납품승인서",
                "발급기관",
                "발급일",
                "서류번호",
                "문서번호");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> values(Map<String, Object> normalizedContext) {
        var raw = normalizedContext == null ? null : normalizedContext.get("values");
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private String value(Map<String, Object> values, String fieldName) {
        var raw = values == null ? null : values.get(fieldName);
        if (raw instanceof Map<?, ?> map) {
            var valueMap = (Map<String, Object>) map;
            return firstNonBlank(text(valueMap.get("canonicalValue")), text(valueMap.get("rawValue")));
        }
        return text(raw);
    }

    private boolean containsAny(String value, String... candidates) {
        var safe = value == null ? "" : value;
        for (var candidate : candidates) {
            if (candidate != null && !candidate.isBlank() && safe.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private int countContains(String value, String needle) {
        if (value == null || value.isBlank() || needle == null || needle.isBlank()) {
            return 0;
        }
        var count = 0;
        var index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private String normalizeKoreanText(String value) {
        return text(value).replaceAll("\\s+", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
