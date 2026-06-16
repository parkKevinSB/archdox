package com.archdox.cloud.engine.inspection.flow;

import com.archdox.cloud.engine.application.EngineExternalReviewSessionService;
import com.archdox.cloud.engine.application.ArchDoxEngineResultStatus;
import com.archdox.cloud.engine.dto.CreateEngineReviewSessionRequest;
import com.archdox.cloud.engine.dto.EngineContextFactRequest;
import com.archdox.cloud.engine.dto.SubmitEngineReviewDocumentRequest;
import com.archdox.cloud.engine.dto.SubmitEngineReviewFactsRequest;
import com.archdox.cloud.engine.inspection.InspectionDocumentTextExtractionService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class InspectionDocumentReviewFlowService {
    private final EngineExternalReviewSessionService reviewSessionService;
    private final InspectionDocumentTextExtractionService extractionService;

    public InspectionDocumentReviewFlowService(
            EngineExternalReviewSessionService reviewSessionService,
            InspectionDocumentTextExtractionService extractionService
    ) {
        this.reviewSessionService = reviewSessionService;
        this.extractionService = extractionService;
    }

    public void createAndSubmitDocument(InspectionDocumentReviewRequest request) {
        var session = reviewSessionService.create(new CreateEngineReviewSessionRequest(
                request.customerProjectRef(),
                request.reviewPurpose()), request.principal());
        request.state().reviewSessionId(session.reviewSessionId());
        reviewSessionService.submitDocument(session.reviewSessionId(), new SubmitEngineReviewDocumentRequest(
                request.documentTypeHint(),
                request.fileName(),
                request.contentText()), request.principal());
    }

    public void extractAndSubmitFacts(InspectionDocumentReviewRequest request) {
        var extraction = extractionService.extract(
                request.contentText(),
                request.targetDate(),
                request.documentTypeHint());
        var facts = new ArrayList<EngineContextFactRequest>();
        facts.addAll(extraction.facts());
        facts.addAll(request.suppliedFacts());
        var metadata = new LinkedHashMap<String, Object>();
        metadata.putAll(request.inputMetadata());
        metadata.putAll(extraction.metadata());
        request.state().extractionMetadata(metadata);
        reviewSessionService.submitFacts(
                request.state().reviewSessionId(),
                new SubmitEngineReviewFactsRequest(List.copyOf(facts)),
                request.principal());
    }

    public void normalize(InspectionDocumentReviewRequest request) {
        request.state().normalizedResponse(reviewSessionService.normalize(
                request.state().reviewSessionId(),
                request.principal()));
    }

    public void runValidation(InspectionDocumentReviewRequest request) {
        request.state().validationResponse(reviewSessionService.runValidation(
                request.state().reviewSessionId(),
                request.principal()));
    }

    public void complete(InspectionDocumentReviewRequest request) {
        var validation = request.state().validationResponse();
        var normalized = request.state().normalizedResponse();
        var validationResult = validation == null ? null : validation.validationResult();
        var missingQuestions = normalized == null
                ? List.<Map<String, Object>>of()
                : missingQuestions(normalized.normalizedContext());
        var workflowStatus = workflowStatus(
                targetDateMissing(request.targetDate(), request.state().extractionMetadata()),
                targetDateSelectionRequired(request.targetDate(), request.state().extractionMetadata()),
                missingQuestions,
                validationResult == null ? null : validationResult.status());

        var result = new LinkedHashMap<String, Object>();
        result.put("workflowStatus", workflowStatus);
        result.put("reviewSessionId", request.state().reviewSessionId());
        result.put("documentTypeHint", request.documentTypeHint());
        result.put("targetDate", request.targetDate());
        result.put("extraction", request.state().extractionMetadata());
        result.put("questions", missingQuestions);
        result.put("normalizedContext", normalized == null ? Map.of() : normalized.normalizedContext());
        result.put("validationResult", validationResult);
        request.state().output(Map.copyOf(result));
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
                && (extractionMetadata == null || !Boolean.TRUE.equals(extractionMetadata.get("targetDateMatched")));
    }

    private boolean targetDateSelectionRequired(String targetDate, Map<String, Object> extractionMetadata) {
        return (targetDate == null || targetDate.isBlank())
                && availableDates(extractionMetadata).size() > 1;
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
}
