package com.archdox.cloud.engine.api;

import com.archdox.cloud.engine.application.EngineReviewSessionService;
import com.archdox.cloud.engine.dto.CreateEngineReviewSessionRequest;
import com.archdox.cloud.engine.dto.EngineReviewResultResponse;
import com.archdox.cloud.engine.dto.EngineReviewSessionResponse;
import com.archdox.cloud.engine.dto.SubmitEngineReviewDocumentRequest;
import com.archdox.cloud.engine.dto.SubmitEngineReviewFactsRequest;
import com.archdox.cloud.global.security.UserPrincipal;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/engine/review-sessions")
public class EngineReviewSessionController {
    private final EngineReviewSessionService service;

    public EngineReviewSessionController(EngineReviewSessionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EngineReviewSessionResponse create(
            @Valid @RequestBody CreateEngineReviewSessionRequest request,
            Authentication authentication
    ) {
        return service.create(request, principal(authentication));
    }

    @GetMapping("/{reviewSessionId}")
    public EngineReviewSessionResponse get(
            @PathVariable String reviewSessionId,
            Authentication authentication
    ) {
        return service.get(reviewSessionId, principal(authentication));
    }

    @GetMapping("/{reviewSessionId}/result")
    public EngineReviewResultResponse getResult(
            @PathVariable String reviewSessionId,
            Authentication authentication
    ) {
        return service.getResult(reviewSessionId, principal(authentication));
    }

    @PostMapping("/{reviewSessionId}/documents")
    public EngineReviewSessionResponse submitDocument(
            @PathVariable String reviewSessionId,
            @Valid @RequestBody SubmitEngineReviewDocumentRequest request,
            Authentication authentication
    ) {
        return service.submitDocument(reviewSessionId, request, principal(authentication));
    }

    @PostMapping("/{reviewSessionId}/facts")
    public EngineReviewSessionResponse submitFacts(
            @PathVariable String reviewSessionId,
            @Valid @RequestBody SubmitEngineReviewFactsRequest request,
            Authentication authentication
    ) {
        return service.submitFacts(reviewSessionId, request, principal(authentication));
    }

    @PostMapping("/{reviewSessionId}/normalize")
    public EngineReviewSessionResponse normalize(
            @PathVariable String reviewSessionId,
            Authentication authentication
    ) {
        return service.normalize(reviewSessionId, principal(authentication));
    }

    @PostMapping("/{reviewSessionId}/run-validation")
    public EngineReviewSessionResponse runValidation(
            @PathVariable String reviewSessionId,
            Authentication authentication
    ) {
        return service.runValidation(reviewSessionId, principal(authentication));
    }

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
