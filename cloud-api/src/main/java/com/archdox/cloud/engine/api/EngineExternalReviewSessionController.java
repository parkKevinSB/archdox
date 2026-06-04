package com.archdox.cloud.engine.api;

import com.archdox.cloud.engine.application.EngineReviewSessionService;
import com.archdox.cloud.engine.auth.application.EngineApiKeyManagementService;
import com.archdox.cloud.engine.auth.application.EngineApiPrincipal;
import com.archdox.cloud.engine.dto.CreateEngineReviewSessionRequest;
import com.archdox.cloud.engine.dto.EngineReviewResultResponse;
import com.archdox.cloud.engine.dto.EngineReviewSessionResponse;
import com.archdox.cloud.engine.dto.SubmitEngineReviewDocumentRequest;
import com.archdox.cloud.engine.dto.SubmitEngineReviewFactsRequest;
import com.archdox.cloud.engine.usage.application.EngineApiQuotaGuardService;
import com.archdox.cloud.engine.usage.application.EngineApiUsageService;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/engine/external/review-sessions")
public class EngineExternalReviewSessionController {
    private final EngineReviewSessionService service;
    private final EngineApiQuotaGuardService quotaGuardService;
    private final EngineApiUsageService usageService;

    public EngineExternalReviewSessionController(
            EngineReviewSessionService service,
            EngineApiQuotaGuardService quotaGuardService,
            EngineApiUsageService usageService
    ) {
        this.service = service;
        this.quotaGuardService = quotaGuardService;
        this.usageService = usageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EngineReviewSessionResponse create(
            @Valid @RequestBody CreateEngineReviewSessionRequest request,
            Authentication authentication
    ) {
        var principal = authorize(authentication, "CREATE_REVIEW_SESSION");
        var response = service.create(request, principal);
        recordUsage(principal, "CREATE_REVIEW_SESSION", response.reviewSessionId(), Map.of(
                "reviewPurpose", response.reviewPurpose(),
                "customerProjectRefPresent", response.customerProjectRef() != null && !response.customerProjectRef().isBlank()));
        return response;
    }

    @GetMapping("/{reviewSessionId}")
    public EngineReviewSessionResponse get(
            @PathVariable String reviewSessionId,
            Authentication authentication
    ) {
        var principal = authorize(authentication, "GET_REVIEW_SESSION");
        var response = service.get(reviewSessionId, principal);
        recordUsage(principal, "GET_REVIEW_SESSION", response.reviewSessionId(), Map.of(
                "sessionStatus", response.status()));
        return response;
    }

    @GetMapping("/{reviewSessionId}/result")
    public EngineReviewResultResponse getResult(
            @PathVariable String reviewSessionId,
            Authentication authentication
    ) {
        var principal = authorize(authentication, "GET_REVIEW_RESULT");
        var response = service.getResult(reviewSessionId, principal);
        recordUsage(principal, "GET_REVIEW_RESULT", response.reviewSessionId(), Map.of(
                "sessionStatus", response.status(),
                "resultReady", response.resultReady(),
                "engineStatus", response.validationResult().status().name(),
                "findingCount", response.validationResult().findings().size()));
        return response;
    }

    @PostMapping("/{reviewSessionId}/documents")
    public EngineReviewSessionResponse submitDocument(
            @PathVariable String reviewSessionId,
            @Valid @RequestBody SubmitEngineReviewDocumentRequest request,
            Authentication authentication
    ) {
        var principal = authorize(authentication, "SUBMIT_DOCUMENT");
        var response = service.submitDocument(reviewSessionId, request, principal);
        recordUsage(principal, "SUBMIT_DOCUMENT", response.reviewSessionId(), metadata(
                "documentTypeHint", request.documentTypeHint(),
                "fileName", request.fileName(),
                "contentLength", request.contentText() == null ? 0 : request.contentText().length()));
        return response;
    }

    @PostMapping("/{reviewSessionId}/facts")
    public EngineReviewSessionResponse submitFacts(
            @PathVariable String reviewSessionId,
            @Valid @RequestBody SubmitEngineReviewFactsRequest request,
            Authentication authentication
    ) {
        var principal = authorize(authentication, "SUBMIT_FACTS");
        var response = service.submitFacts(reviewSessionId, request, principal);
        recordUsage(principal, "SUBMIT_FACTS", response.reviewSessionId(), Map.of(
                "factCount", request.facts().size()));
        return response;
    }

    @PostMapping("/{reviewSessionId}/normalize")
    public EngineReviewSessionResponse normalize(
            @PathVariable String reviewSessionId,
            Authentication authentication
    ) {
        var principal = authorize(authentication, "NORMALIZE_CONTEXT");
        var response = service.normalize(reviewSessionId, principal);
        recordUsage(principal, "NORMALIZE_CONTEXT", response.reviewSessionId(), Map.of(
                "missingQuestionCount", listSize(response.normalizedContext().get("missingQuestions")),
                "ambiguityCount", listSize(response.normalizedContext().get("ambiguities"))));
        return response;
    }

    @PostMapping("/{reviewSessionId}/run-validation")
    public EngineReviewSessionResponse runValidation(
            @PathVariable String reviewSessionId,
            Authentication authentication
    ) {
        var principal = authorize(authentication, "RUN_VALIDATION");
        var response = service.runValidation(reviewSessionId, principal);
        recordUsage(principal, "RUN_VALIDATION", response.reviewSessionId(), Map.of(
                "engineStatus", response.validationResult().status().name(),
                "findingCount", response.validationResult().findings().size(),
                "generationAllowed", response.validationResult().generationAllowed()));
        return response;
    }

    private EngineApiPrincipal apiPrincipal(Authentication authentication) {
        return (EngineApiPrincipal) authentication.getPrincipal();
    }

    private EngineApiPrincipal authorize(Authentication authentication, String operation) {
        var principal = apiPrincipal(authentication);
        principal.requireScope(EngineApiKeyManagementService.SCOPE_REVIEW_SESSION);
        quotaGuardService.requireReviewSessionQuota(principal, operation);
        return principal;
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

    private int listSize(Object value) {
        if (value instanceof java.util.List<?> list) {
            return list.size();
        }
        return 0;
    }
}
