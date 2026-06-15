package com.archdox.cloud.engine.inspection;

import com.archdox.cloud.engine.application.ArchDoxEngineResultStatus;
import com.archdox.cloud.engine.application.EngineExternalReviewSessionService;
import com.archdox.cloud.engine.auth.application.EngineApiPrincipal;
import com.archdox.cloud.engine.dto.EngineContextFactRequest;
import com.archdox.cloud.engine.dto.EngineReviewSessionResponse;
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
    private final InspectionDocumentReviewFlowFactory flowFactory;
    private final FlowerFlowAsyncCompletionService flowCompletionService;

    public InspectionDocumentReviewOrchestrationService(
            EngineExternalReviewSessionService reviewSessionService,
            InspectionDocumentReviewFlowFactory flowFactory,
            FlowerFlowAsyncCompletionService flowCompletionService
    ) {
        this.reviewSessionService = reviewSessionService;
        this.flowFactory = flowFactory;
        this.flowCompletionService = flowCompletionService;
    }

    public Map<String, Object> review(
            EngineApiPrincipal principal,
            Map<String, Object> arguments
    ) {
        var state = new InspectionDocumentReviewState();
        var request = new InspectionDocumentReviewRequest(
                UUID.randomUUID().toString(),
                principal,
                optionalText(arguments.get("customerProjectRef")),
                defaultText(arguments.get("reviewPurpose"), "preflight"),
                defaultText(arguments.get("documentTypeHint"), "CONSTRUCTION_DAILY_SUPERVISION_LOG"),
                defaultText(arguments.get("fileName"), "inspection-document.txt"),
                requiredText(arguments.get("contentText"), "contentText is required"),
                optionalText(arguments.get("targetDate")),
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
            return state.output().isEmpty() ? timeoutResult(request) : state.output();
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
        var result = new LinkedHashMap<String, Object>();
        result.put("workflowStatus", workflowStatus(missingQuestions, validationResult == null ? null : validationResult.status()));
        result.put("reviewSessionId", reviewSessionId);
        result.put("documentTypeHint", documentTypeHint == null ? "" : documentTypeHint);
        if (targetDate != null && !targetDate.isBlank()) {
            result.put("targetDate", targetDate);
        }
        result.put("extraction", extractionMetadata == null ? Map.of() : Map.copyOf(extractionMetadata));
        result.put("questions", missingQuestions);
        result.put("normalizedContext", normalized == null ? Map.of() : normalized.normalizedContext());
        result.put("validationResult", validationResult == null ? Map.of() : validationResult);
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
            List<Map<String, Object>> missingQuestions,
            ArchDoxEngineResultStatus validationStatus
    ) {
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

    private Map<String, Object> timeoutResult(InspectionDocumentReviewRequest request) {
        var result = new LinkedHashMap<String, Object>();
        result.put("workflowStatus", "TIMEOUT");
        result.put("reviewSessionId", request.state().reviewSessionId());
        result.put("documentTypeHint", request.documentTypeHint());
        result.put("targetDate", request.targetDate());
        result.put("message", "Inspection document review flow did not complete before timeout.");
        return Map.copyOf(result);
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
