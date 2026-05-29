package com.archdox.cloud.documentai.api;

import com.archdox.cloud.documentai.application.DocumentAiReviewService;
import com.archdox.cloud.documentai.dto.DocumentAiReviewFindingResponse;
import com.archdox.cloud.documentai.dto.DocumentAiReviewRunResponse;
import com.archdox.cloud.documentai.flow.DocumentReviewWorker;
import com.archdox.cloud.global.security.UserPrincipal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DocumentAiReviewController {
    private final DocumentAiReviewService service;
    private final DocumentReviewWorker worker;

    public DocumentAiReviewController(DocumentAiReviewService service, DocumentReviewWorker worker) {
        this.service = service;
        this.worker = worker;
    }

    @PostMapping("/document-jobs/{jobId}/ai-review-runs")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentAiReviewRunResponse requestReview(
            @PathVariable Long jobId,
            Authentication authentication
    ) {
        var submission = service.requestDocumentQaReview(jobId, (UserPrincipal) authentication.getPrincipal());
        worker.submit(submission.flow());
        return submission.response();
    }

    @GetMapping("/document-jobs/{jobId}/ai-review-runs")
    public List<DocumentAiReviewRunResponse> listRuns(@PathVariable Long jobId) {
        return service.listRuns(jobId);
    }

    @GetMapping("/document-jobs/{jobId}/ai-review-runs/{runId}/findings")
    public List<DocumentAiReviewFindingResponse> listFindings(
            @PathVariable Long jobId,
            @PathVariable Long runId
    ) {
        return service.listFindings(jobId, runId);
    }
}
