package com.archdox.cloud.engine.application;

import com.archdox.cloud.engine.auth.application.EngineApiKeyManagementService;
import com.archdox.cloud.engine.auth.application.EngineApiPrincipal;
import com.archdox.cloud.engine.dto.CreateEngineReviewSessionRequest;
import com.archdox.cloud.engine.dto.EngineReviewResultResponse;
import com.archdox.cloud.engine.dto.EngineReviewDocumentSnapshot;
import com.archdox.cloud.engine.dto.EngineReviewSessionResponse;
import com.archdox.cloud.engine.dto.SubmitEngineReviewDocumentRequest;
import com.archdox.cloud.engine.dto.SubmitEngineReviewFactsRequest;
import com.archdox.cloud.engine.dto.EngineLegalReferenceResponse;
import com.archdox.cloud.engine.usage.application.EngineApiQuotaGuardService;
import com.archdox.cloud.engine.usage.application.EngineApiUsageService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EngineExternalReviewSessionService {
    private final EngineReviewSessionService reviewSessionService;
    private final EngineApiQuotaGuardService quotaGuardService;
    private final EngineApiUsageService usageService;

    public EngineExternalReviewSessionService(
            EngineReviewSessionService reviewSessionService,
            EngineApiQuotaGuardService quotaGuardService,
            EngineApiUsageService usageService
    ) {
        this.reviewSessionService = reviewSessionService;
        this.quotaGuardService = quotaGuardService;
        this.usageService = usageService;
    }

    public EngineReviewSessionResponse create(
            CreateEngineReviewSessionRequest request,
            EngineApiPrincipal principal
    ) {
        authorize(principal, "CREATE_REVIEW_SESSION");
        var response = reviewSessionService.create(request, principal);
        recordUsage(principal, "CREATE_REVIEW_SESSION", response.reviewSessionId(), Map.of(
                "reviewPurpose", response.reviewPurpose(),
                "customerProjectRefPresent", response.customerProjectRef() != null && !response.customerProjectRef().isBlank()));
        return response;
    }

    public EngineReviewSessionResponse get(
            String reviewSessionId,
            EngineApiPrincipal principal
    ) {
        authorize(principal, "GET_REVIEW_SESSION");
        var response = reviewSessionService.get(reviewSessionId, principal);
        recordUsage(principal, "GET_REVIEW_SESSION", response.reviewSessionId(), Map.of(
                "sessionStatus", response.status()));
        return response;
    }

    public EngineReviewResultResponse getResult(
            String reviewSessionId,
            EngineApiPrincipal principal
    ) {
        authorize(principal, "GET_REVIEW_RESULT");
        var response = reviewSessionService.getResult(reviewSessionId, principal);
        recordUsage(principal, "GET_REVIEW_RESULT", response.reviewSessionId(), Map.of(
                "sessionStatus", response.status(),
                "resultReady", response.resultReady(),
                "engineStatus", response.validationResult().status().name(),
                "findingCount", response.validationResult().findings().size()));
        return response;
    }

    public EngineReviewDocumentSnapshot documentSnapshot(
            String reviewSessionId,
            EngineApiPrincipal principal
    ) {
        authorize(principal, "GET_REVIEW_DOCUMENT_SNAPSHOT");
        var snapshot = reviewSessionService.documentSnapshot(reviewSessionId, principal);
        recordUsage(principal, "GET_REVIEW_DOCUMENT_SNAPSHOT", snapshot.reviewSessionId(), Map.of(
                "documentTypeHint", snapshot.documentTypeHint() == null ? "" : snapshot.documentTypeHint(),
                "fileName", snapshot.fileName() == null ? "" : snapshot.fileName(),
                "contentLength", snapshot.documentText() == null ? 0 : snapshot.documentText().length(),
                "factCount", snapshot.facts().size()));
        return snapshot;
    }

    public EngineReviewSessionResponse submitDocument(
            String reviewSessionId,
            SubmitEngineReviewDocumentRequest request,
            EngineApiPrincipal principal
    ) {
        authorize(principal, "SUBMIT_DOCUMENT");
        var response = reviewSessionService.submitDocument(reviewSessionId, request, principal);
        recordUsage(principal, "SUBMIT_DOCUMENT", response.reviewSessionId(), metadata(
                "documentTypeHint", request.documentTypeHint(),
                "fileName", request.fileName(),
                "contentLength", request.contentText() == null ? 0 : request.contentText().length()));
        return response;
    }

    public EngineReviewSessionResponse submitFacts(
            String reviewSessionId,
            SubmitEngineReviewFactsRequest request,
            EngineApiPrincipal principal
    ) {
        authorize(principal, "SUBMIT_FACTS");
        var response = reviewSessionService.submitFacts(reviewSessionId, request, principal);
        recordUsage(principal, "SUBMIT_FACTS", response.reviewSessionId(), Map.of(
                "factCount", request.facts().size()));
        return response;
    }

    public EngineReviewSessionResponse normalize(
            String reviewSessionId,
            EngineApiPrincipal principal
    ) {
        authorize(principal, "NORMALIZE_CONTEXT");
        var response = reviewSessionService.normalize(reviewSessionId, principal);
        recordUsage(principal, "NORMALIZE_CONTEXT", response.reviewSessionId(), Map.of(
                "missingQuestionCount", listSize(response.normalizedContext().get("missingQuestions")),
                "ambiguityCount", listSize(response.normalizedContext().get("ambiguities"))));
        return response;
    }

    public EngineReviewSessionResponse runValidation(
            String reviewSessionId,
            EngineApiPrincipal principal
    ) {
        authorize(principal, "RUN_VALIDATION");
        var response = reviewSessionService.runValidation(reviewSessionId, principal);
        recordUsage(principal, "RUN_VALIDATION", response.reviewSessionId(), validationMetadata(response));
        return response;
    }

    public void requireQuota(EngineApiPrincipal principal, String operation, int requestUnits) {
        principal.requireScope(EngineApiKeyManagementService.SCOPE_REVIEW_SESSION);
        quotaGuardService.requireReviewSessionQuota(principal, operation, requestUnits);
    }

    private void authorize(EngineApiPrincipal principal, String operation) {
        principal.requireScope(EngineApiKeyManagementService.SCOPE_REVIEW_SESSION);
        quotaGuardService.requireReviewSessionQuota(principal, operation);
    }

    private void recordUsage(
            EngineApiPrincipal principal,
            String operation,
            String reviewSessionId,
            Map<String, Object> metadata
    ) {
        usageService.recordReviewSessionUsage(principal, operation, reviewSessionId, metadata);
    }

    private Map<String, Object> metadata(Object... values) {
        var metadata = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            var key = String.valueOf(values[i]);
            var value = values[i + 1];
            if (value != null) {
                metadata.put(key, value);
            }
        }
        return Map.copyOf(metadata);
    }

    private Map<String, Object> validationMetadata(EngineReviewSessionResponse response) {
        var result = response.validationResult();
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("engineStatus", result.status().name());
        metadata.put("findingCount", result.findings().size());
        metadata.put("generationAllowed", result.generationAllowed());
        metadata.put("legalReferenceCount", result.legalReferences().size());
        metadata.put("legalReferenceIds", result.legalReferences().stream()
                .map(EngineLegalReferenceResponse::referenceId)
                .filter(value -> value != null && !value.isBlank())
                .limit(10)
                .toList());
        metadata.put("legalReferenceSources", result.legalReferences().stream()
                .map(this::legalReferenceSource)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(10)
                .toList());
        metadata.put("findingCodes", result.findings().stream()
                .map(finding -> finding.code())
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(10)
                .toList());
        return Map.copyOf(metadata);
    }

    private String legalReferenceSource(EngineLegalReferenceResponse reference) {
        var sourceCode = stringValue(reference.metadata().get("sourceCode"));
        if (!sourceCode.isBlank()) {
            return sourceCode;
        }
        return stringValue(reference.metadata().get("resolutionSource"));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int listSize(Object value) {
        if (value instanceof java.util.List<?> list) {
            return list.size();
        }
        return 0;
    }
}
