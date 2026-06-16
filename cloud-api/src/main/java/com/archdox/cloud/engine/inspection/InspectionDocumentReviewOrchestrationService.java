package com.archdox.cloud.engine.inspection;

import com.archdox.cloud.engine.application.ArchDoxEngineResultStatus;
import com.archdox.cloud.engine.application.EngineExternalReviewSessionService;
import com.archdox.cloud.engine.auth.application.EngineApiPrincipal;
import com.archdox.cloud.engine.dto.EngineContextFactRequest;
import com.archdox.cloud.engine.dto.EngineFindingResponse;
import com.archdox.cloud.engine.dto.EngineReviewSessionResponse;
import com.archdox.cloud.engine.dto.EngineValidationResultResponse;
import com.archdox.cloud.engine.dto.SubmitEngineReviewFactsRequest;
import com.archdox.cloud.engine.inspection.flow.InspectionDocumentReviewFlowFactory;
import com.archdox.cloud.engine.inspection.flow.InspectionDocumentReviewRequest;
import com.archdox.cloud.engine.inspection.flow.InspectionDocumentReviewState;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.event.ArchDoxRuntimeConfiguration;
import com.archdox.cloud.global.flow.FlowerFlowAsyncCompletionService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.springframework.stereotype.Service;

@Service
public class InspectionDocumentReviewOrchestrationService {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(120);

    private final EngineExternalReviewSessionService reviewSessionService;
    private final InspectionDocumentContentTextService contentTextService;
    private final InspectionDocumentReviewFlowFactory flowFactory;
    private final FlowerFlowAsyncCompletionService flowCompletionService;

    public InspectionDocumentReviewOrchestrationService(
            EngineExternalReviewSessionService reviewSessionService,
            InspectionDocumentContentTextService contentTextService,
            InspectionDocumentReviewFlowFactory flowFactory,
            FlowerFlowAsyncCompletionService flowCompletionService
    ) {
        this.reviewSessionService = reviewSessionService;
        this.contentTextService = contentTextService;
        this.flowFactory = flowFactory;
        this.flowCompletionService = flowCompletionService;
    }

    public Map<String, Object> review(
            EngineApiPrincipal principal,
            Map<String, Object> arguments
    ) {
        var state = new InspectionDocumentReviewState();
        var resolvedContent = contentTextService.resolve(arguments);
        var request = new InspectionDocumentReviewRequest(
                UUID.randomUUID().toString(),
                principal,
                optionalText(arguments.get("customerProjectRef")),
                defaultText(arguments.get("reviewPurpose"), "preflight"),
                defaultText(arguments.get("documentTypeHint"), "CONSTRUCTION_DAILY_SUPERVISION_LOG"),
                defaultText(arguments.get("fileName"), "inspection-document.txt"),
                resolvedContent.contentText(),
                optionalText(arguments.get("targetDate")),
                resolvedContent.metadata(),
                facts(arguments.get("facts")),
                state);
        var flow = flowFactory.create(request);
        var timeout = timeout(arguments.get("timeoutSeconds"));
        try {
            var completed = flowCompletionService
                    .submitAndTrackTerminal(ArchDoxRuntimeConfiguration.DOCUMENT_REVIEW_WORKER, flow, timeout)
                    .join();
            if (!completed) {
                return timeoutResult(request);
            }
            if (state.reviewSessionId().isBlank()) {
                return timeoutResult(request);
            }
            return output(
                    state.reviewSessionId(),
                    request.documentTypeHint(),
                    request.targetDate(),
                    state.extractionMetadata(),
                    state.normalizedResponse(),
                    state.validationResponse());
        } catch (CompletionException ex) {
            throw rethrow(ex);
        }
    }

    public Map<String, Object> answer(
            EngineApiPrincipal principal,
            Map<String, Object> arguments
    ) {
        var reviewSessionId = requiredText(arguments.get("reviewSessionId"), "reviewSessionId is required");
        var answers = facts(arguments.get("facts"), "USER_PROVIDED");
        if (answers.isEmpty()) {
            throw new BadRequestException("facts are required");
        }
        var current = reviewSessionService.get(reviewSessionId, principal);
        var mergedFacts = mergeFacts(current.facts(), answers);
        reviewSessionService.submitFacts(reviewSessionId, new SubmitEngineReviewFactsRequest(mergedFacts), principal);
        var normalized = reviewSessionService.normalize(reviewSessionId, principal);
        var validation = reviewSessionService.runValidation(reviewSessionId, principal);
        return output(reviewSessionId, current.documentTypeHint(), null, Map.of(), normalized, validation);
    }

    public Map<String, Object> state(
            EngineApiPrincipal principal,
            Map<String, Object> arguments
    ) {
        var reviewSessionId = requiredText(arguments.get("reviewSessionId"), "reviewSessionId is required");
        var session = reviewSessionService.get(reviewSessionId, principal);
        return output(reviewSessionId, session.documentTypeHint(), null, Map.of(), session, session);
    }

    private List<EngineContextFactRequest> mergeFacts(
            List<Map<String, Object>> existingFacts,
            List<EngineContextFactRequest> answers
    ) {
        var merged = new LinkedHashMap<String, EngineContextFactRequest>();
        for (var fact : existingFacts == null ? List.<Map<String, Object>>of() : existingFacts) {
            var request = factRequest(fact, null);
            if (request != null) {
                merged.put(request.resolvedFieldName(), request);
            }
        }
        for (var answer : answers) {
            if (answer.resolvedFieldName().isBlank()) {
                continue;
            }
            merged.put(answer.resolvedFieldName(), answer);
        }
        return List.copyOf(merged.values());
    }

    private EngineContextFactRequest factRequest(Map<String, Object> map, String fallbackSource) {
        var fieldName = firstNonBlank(optionalText(map.get("fieldName")), optionalText(map.get("name")));
        var rawValue = optionalText(map.get("rawValue"));
        if (fieldName == null || fieldName.isBlank() || rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return new EngineContextFactRequest(
                fieldName,
                fieldName,
                rawValue,
                firstNonBlank(optionalText(map.get("source")), fallbackSource),
                optionalText(map.get("evidence")),
                doubleValue(map.get("confidence")));
    }

    public Map<String, Object> output(
            String reviewSessionId,
            String documentTypeHint,
            String targetDate,
            Map<String, Object> extractionMetadata,
            EngineReviewSessionResponse normalized,
            EngineReviewSessionResponse validation
    ) {
        var validationResult = validation == null ? null : validation.validationResult();
        var missingQuestions = normalized == null
                ? List.<Map<String, Object>>of()
                : missingQuestions(normalized.normalizedContext());
        var targetDateMissing = targetDateMissing(targetDate, extractionMetadata);
        var targetDateSelectionRequired = targetDateSelectionRequired(targetDate, extractionMetadata);
        var effectiveQuestions = targetDateMissing || targetDateSelectionRequired
                ? prependDateQuestion(targetDate, extractionMetadata, missingQuestions)
                : missingQuestions;
        var effectiveValidationResult = validationResult;
        if (targetDateMissing) {
            effectiveValidationResult = dateNotFoundValidationResult(targetDate, extractionMetadata, validationResult);
        } else if (targetDateSelectionRequired) {
            effectiveValidationResult = dateSelectionRequiredValidationResult(extractionMetadata, validationResult);
        }
        var workflowStatus = workflowStatus(
                targetDateMissing,
                targetDateSelectionRequired,
                effectiveQuestions,
                effectiveValidationResult == null ? null : effectiveValidationResult.status());
        var result = new LinkedHashMap<String, Object>();
        result.put("workflowStatus", workflowStatus);
        result.put("reviewSessionId", reviewSessionId);
        result.put("documentTypeHint", documentTypeHint == null ? "" : documentTypeHint);
        if (targetDate != null && !targetDate.isBlank()) {
            result.put("targetDate", targetDate);
        }
        result.put("assistantMessage", assistantMessage(
                workflowStatus,
                targetDate,
                availableDates(extractionMetadata),
                effectiveQuestions,
                effectiveValidationResult));
        result.put("contextSummary", contextSummary(effectiveQuestions, normalized, effectiveValidationResult, extractionMetadata));
        result.put("nextActions", nextActions(workflowStatus, reviewSessionId, effectiveQuestions, effectiveValidationResult, extractionMetadata));
        result.put("extraction", extractionMetadata == null ? Map.of() : Map.copyOf(extractionMetadata));
        result.put("questions", effectiveQuestions);
        result.put("normalizedContext", normalized == null ? Map.of() : normalized.normalizedContext());
        result.put("validationResult", effectiveValidationResult == null ? Map.of() : effectiveValidationResult);
        return Map.copyOf(result);
    }

    private String assistantMessage(
            String workflowStatus,
            String targetDate,
            List<String> availableDates,
            List<Map<String, Object>> missingQuestions,
            EngineValidationResultResponse validationResult
    ) {
        if ("DATE_SELECTION_REQUIRED".equals(workflowStatus)) {
            return "문서에서 여러 공사감리일지를 찾았습니다. 검토할 일자를 선택해주세요: "
                    + availableDateSummary(availableDates);
        }
        return switch (workflowStatus) {
            case "DATE_NOT_FOUND" -> "요청한 "
                    + firstNonBlank(targetDate, "대상 일자")
                    + " 공사감리일지를 문서에서 찾지 못했습니다. 문서에서 확인된 일자 "
                    + availableDateSummary(availableDates)
                    + " 중 어떤 일지를 검토할지 다시 지정하세요.";
            case "NEEDS_INPUT" -> "문서에서 감리 항목을 추출했지만 추가 문맥 "
                    + missingQuestions.size()
                    + "건이 필요합니다. answer_inspection_document_questions 도구로 답변을 제출하세요.";
            case "VALIDATED" -> "문서 검토가 완료됐습니다. 현재 입력과 근거 범위에서는 검토 통과 상태입니다.";
            case "BLOCKED" -> "문서 생성 또는 검토 진행을 막는 항목이 있습니다. finding을 보완한 뒤 다시 검토하세요.";
            default -> {
                var findingCount = validationResult == null ? 0 : validationResult.findings().size();
                yield findingCount > 0
                        ? "검토가 완료됐지만 사람 확인이 필요한 항목 " + findingCount + "건이 있습니다."
                        : "검토 상태를 확인했습니다. 추가 확인이 필요할 수 있습니다.";
            }
        };
    }

    private Map<String, Object> contextSummary(
            List<Map<String, Object>> missingQuestions,
            EngineReviewSessionResponse normalized,
            EngineValidationResultResponse validationResult,
            Map<String, Object> extractionMetadata
    ) {
        var normalizedContext = normalized == null ? Map.<String, Object>of() : normalized.normalizedContext();
        var summary = new LinkedHashMap<String, Object>();
        summary.put("missingQuestionCount", missingQuestions.size());
        summary.put("catalogSelectionCount", listSize(normalizedContext.get("catalogSelections")));
        summary.put("findingCount", validationResult == null ? 0 : validationResult.findings().size());
        summary.put("legalReferenceCount", validationResult == null ? 0 : validationResult.legalReferences().size());
        summary.put("generationAllowed", validationResult != null && validationResult.generationAllowed());
        summary.put("engineStatus", validationResult == null ? "PENDING" : validationResult.status().name());
        summary.put("targetDateMatched", targetDateMatched(extractionMetadata));
        summary.put("availableDateCount", availableDates(extractionMetadata).size());
        return Map.copyOf(summary);
    }

    private List<Map<String, Object>> nextActions(
            String workflowStatus,
            String reviewSessionId,
            List<Map<String, Object>> missingQuestions,
            EngineValidationResultResponse validationResult,
            Map<String, Object> extractionMetadata
    ) {
        var actions = new ArrayList<Map<String, Object>>();
        if ("DATE_NOT_FOUND".equals(workflowStatus) || "DATE_SELECTION_REQUIRED".equals(workflowStatus)) {
            actions.add(Map.of(
                    "code", "CHOOSE_AVAILABLE_DATE",
                    "label", "대상 일자 다시 선택",
                    "actionType", "USER_ACTION",
                    "blocking", true,
                    "targetTool", "review_inspection_document",
                    "arguments", Map.of(
                            "reviewSessionId", reviewSessionId,
                            "targetDateOptions", availableDates(extractionMetadata))));
            return List.copyOf(actions);
        }
        if ("NEEDS_INPUT".equals(workflowStatus)) {
            actions.add(Map.of(
                    "code", "ANSWER_MISSING_CONTEXT",
                    "label", "부족한 문맥 답변",
                    "actionType", "MCP_TOOL",
                    "blocking", true,
                    "targetTool", "answer_inspection_document_questions",
                    "arguments", Map.of(
                            "reviewSessionId", reviewSessionId,
                            "facts", missingQuestions.stream()
                                    .map(this::factTemplate)
                                    .toList())));
            return List.copyOf(actions);
        }
        if ("VALIDATED".equals(workflowStatus)) {
            actions.add(Map.of(
                    "code", "REVIEW_VALIDATED",
                    "label", "검토 결과 조회",
                    "actionType", "MCP_TOOL",
                    "blocking", false,
                    "targetTool", "get_inspection_document_review_state",
                    "arguments", Map.of("reviewSessionId", reviewSessionId)));
            return List.copyOf(actions);
        }
        if ("BLOCKED".equals(workflowStatus)) {
            actions.add(Map.of(
                    "code", "FIX_BLOCKING_FINDINGS",
                    "label", "차단 finding 보완",
                    "actionType", "USER_ACTION",
                    "blocking", true,
                    "targetTool", "",
                    "arguments", Map.of("reviewSessionId", reviewSessionId)));
            return List.copyOf(actions);
        }
        actions.add(Map.of(
                "code", "HUMAN_REVIEW_REQUIRED",
                "label", "사람 확인 필요",
                "actionType", "USER_ACTION",
                "blocking", false,
                "targetTool", "",
                "arguments", Map.of(
                        "reviewSessionId", reviewSessionId,
                        "findingCount", validationResult == null ? 0 : validationResult.findings().size())));
        return List.copyOf(actions);
    }

    private Map<String, Object> factTemplate(Map<String, Object> question) {
        var fieldName = optionalText(question.get("fieldName"));
        var result = new LinkedHashMap<String, Object>();
        result.put("name", fieldName == null ? "" : fieldName);
        result.put("rawValue", "");
        result.put("source", "USER_PROVIDED");
        result.put("confidence", 0.95d);
        result.put("question", firstNonBlank(optionalText(question.get("question")), ""));
        return Map.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> missingQuestions(Map<String, Object> normalizedContext) {
        var value = normalizedContext == null ? null : normalizedContext.get("missingQuestions");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> Map.copyOf((Map<String, Object>) item))
                .toList();
    }

    private String workflowStatus(
            boolean targetDateMissing,
            boolean targetDateSelectionRequired,
            List<Map<String, Object>> missingQuestions,
            ArchDoxEngineResultStatus validationStatus
    ) {
        if (targetDateMissing) {
            return "DATE_NOT_FOUND";
        }
        if (targetDateSelectionRequired) {
            return "DATE_SELECTION_REQUIRED";
        }
        if (missingQuestions != null && !missingQuestions.isEmpty()) {
            return "NEEDS_INPUT";
        }
        if (validationStatus == ArchDoxEngineResultStatus.PASS) {
            return "VALIDATED";
        }
        if (validationStatus == ArchDoxEngineResultStatus.FAIL) {
            return "BLOCKED";
        }
        return "REVIEW_REQUIRED";
    }

    private boolean targetDateMissing(String targetDate, Map<String, Object> extractionMetadata) {
        return targetDate != null
                && !targetDate.isBlank()
                && !targetDateMatched(extractionMetadata);
    }

    private boolean targetDateSelectionRequired(String targetDate, Map<String, Object> extractionMetadata) {
        return (targetDate == null || targetDate.isBlank())
                && availableDates(extractionMetadata).size() > 1;
    }

    private boolean targetDateMatched(Map<String, Object> extractionMetadata) {
        return extractionMetadata != null
                && Boolean.TRUE.equals(extractionMetadata.get("targetDateMatched"));
    }

    @SuppressWarnings("unchecked")
    private List<String> availableDates(Map<String, Object> extractionMetadata) {
        var value = extractionMetadata == null ? null : extractionMetadata.get("availableDates");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(String::valueOf)
                .filter(date -> date != null && !date.isBlank())
                .distinct()
                .toList();
    }

    private List<Map<String, Object>> prependDateQuestion(
            String targetDate,
            Map<String, Object> extractionMetadata,
            List<Map<String, Object>> missingQuestions
    ) {
        var question = new LinkedHashMap<String, Object>();
        var availableDates = availableDates(extractionMetadata);
        question.put("fieldName", "targetDate");
        question.put("questionType", "DATE_SELECTION");
        question.put("question", "요청한 "
                + firstNonBlank(targetDate, "대상 일자")
                + " 공사감리일지를 문서에서 찾지 못했습니다. 문서에 있는 일자 중 어느 일지를 검토할까요?");
        if (targetDate == null || targetDate.isBlank()) {
            question.put("question", "문서에서 여러 공사감리일지를 찾았습니다. 어느 일지를 검토할까요?");
        }
        question.put("requestedTargetDate", firstNonBlank(targetDate, ""));
        question.put("options", availableDates);
        question.put("blocking", true);
        var questions = new ArrayList<Map<String, Object>>();
        questions.add(Map.copyOf(question));
        questions.addAll(missingQuestions == null ? List.of() : missingQuestions);
        return List.copyOf(questions);
    }

    private EngineValidationResultResponse dateNotFoundValidationResult(
            String targetDate,
            Map<String, Object> extractionMetadata,
            EngineValidationResultResponse original
    ) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("dateGate", true);
        metadata.put("requestedTargetDate", firstNonBlank(targetDate, ""));
        metadata.put("availableDates", availableDates(extractionMetadata));
        if (original != null) {
            metadata.put("originalStatus", original.status().name());
            metadata.put("originalGenerationAllowed", original.generationAllowed());
            metadata.put("originalFindingCount", original.findings().size());
        }
        return new EngineValidationResultResponse(
                original == null ? "" : original.engineRunId(),
                ArchDoxEngineResultStatus.WARN,
                false,
                "Requested target date was not found in the submitted inspection document.",
                List.of(new EngineFindingResponse(
                        "TARGET_DATE_NOT_FOUND",
                        "COMPLETENESS",
                        "MEDIUM",
                        "CODE_VALIDATION",
                        "targetDate",
                        "요청한 공사감리일자를 문서에서 찾지 못했습니다. 문서에 포함된 일자 중 다시 선택해야 합니다.",
                        List.of(),
                        Map.copyOf(metadata))),
                List.of(),
                List.of(),
                "DATE_SELECTION_REQUIRED",
                List.of(),
                "DOCUMENT_REVIEW_INPUT_GATE",
                Map.copyOf(metadata));
    }

    private EngineValidationResultResponse dateSelectionRequiredValidationResult(
            Map<String, Object> extractionMetadata,
            EngineValidationResultResponse original
    ) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("dateGate", true);
        metadata.put("availableDates", availableDates(extractionMetadata));
        if (original != null) {
            metadata.put("originalStatus", original.status().name());
            metadata.put("originalGenerationAllowed", original.generationAllowed());
            metadata.put("originalFindingCount", original.findings().size());
        }
        return new EngineValidationResultResponse(
                original == null ? "" : original.engineRunId(),
                ArchDoxEngineResultStatus.WARN,
                false,
                "문서에서 여러 공사감리일지를 찾았습니다. 검토할 일자를 먼저 선택해야 합니다.",
                List.of(new EngineFindingResponse(
                        "TARGET_DATE_REQUIRED",
                        "COMPLETENESS",
                        "MEDIUM",
                        "CODE_VALIDATION",
                        "targetDate",
                        "문서에서 여러 공사감리일지가 발견되었습니다. 검토할 일자를 선택해야 합니다.",
                        List.of(),
                        Map.copyOf(metadata))),
                List.of(),
                List.of(),
                "DATE_SELECTION_REQUIRED",
                List.of(),
                "DOCUMENT_REVIEW_INPUT_GATE",
                Map.copyOf(metadata));
    }

    private String availableDateSummary(List<String> availableDates) {
        if (availableDates == null || availableDates.isEmpty()) {
            return "없음";
        }
        if (availableDates.size() <= 5) {
            return String.join(", ", availableDates);
        }
        return String.join(", ", availableDates.subList(0, 5)) + " 외 " + (availableDates.size() - 5) + "건";
    }

    private Map<String, Object> timeoutResult(InspectionDocumentReviewRequest request) {
        var result = new LinkedHashMap<String, Object>();
        result.put("workflowStatus", "TIMEOUT");
        result.put("reviewSessionId", request.state().reviewSessionId());
        result.put("documentTypeHint", request.documentTypeHint());
        result.put("targetDate", request.targetDate());
        result.put("assistantMessage", "문서검토 flow가 제한 시간 안에 끝나지 않았습니다. 같은 reviewSessionId로 상태를 다시 조회하세요.");
        result.put("nextActions", List.of(Map.of(
                "code", "CHECK_REVIEW_STATE",
                "label", "문서검토 상태 조회",
                "actionType", "MCP_TOOL",
                "blocking", false,
                "targetTool", "get_inspection_document_review_state",
                "arguments", Map.of("reviewSessionId", request.state().reviewSessionId()))));
        result.put("message", "Inspection document review flow did not complete before timeout.");
        return Map.copyOf(result);
    }

    private int listSize(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }

    private RuntimeException rethrow(CompletionException ex) {
        if (ex.getCause() instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return ex;
    }

    private List<EngineContextFactRequest> facts(Object value) {
        return facts(value, null);
    }

    @SuppressWarnings("unchecked")
    private List<EngineContextFactRequest> facts(Object value, String fallbackSource) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        var facts = new ArrayList<EngineContextFactRequest>();
        for (var item : list) {
            if (item instanceof Map<?, ?> map) {
                var fact = factRequest((Map<String, Object>) map, fallbackSource);
                if (fact != null) {
                    facts.add(fact);
                }
            }
        }
        return List.copyOf(facts);
    }

    private Duration timeout(Object value) {
        var seconds = intValue(value, (int) DEFAULT_TIMEOUT.toSeconds());
        seconds = Math.max(1, Math.min(seconds, (int) MAX_TIMEOUT.toSeconds()));
        return Duration.ofSeconds(seconds);
    }

    private String requiredText(Object value, String message) {
        var text = optionalText(value);
        if (text == null || text.isBlank()) {
            throw new BadRequestException(message);
        }
        return text;
    }

    private String defaultText(Object value, String fallback) {
        var text = optionalText(value);
        return text == null || text.isBlank() ? fallback : text;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? null : second.trim();
    }

    private String optionalText(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        var text = optionalText(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        var text = optionalText(value);
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
