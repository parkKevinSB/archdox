package com.archdox.cloud.document.api;

import com.archdox.cloud.document.application.DocumentGenerationRequestService;
import com.archdox.cloud.document.application.DocumentJobService;
import com.archdox.cloud.document.dto.CreateDocumentJobRequest;
import com.archdox.cloud.document.dto.DocumentHtmlPreviewResponse;
import com.archdox.cloud.document.dto.DocumentJobResponse;
import com.archdox.cloud.global.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/v1")
public class DocumentJobController {
    private final DocumentJobService documentJobService;
    private final DocumentGenerationRequestService requestService;

    public DocumentJobController(
            DocumentJobService documentJobService,
            DocumentGenerationRequestService requestService
    ) {
        this.documentJobService = documentJobService;
        this.requestService = requestService;
    }

    @PostMapping("/inspection-reports/{reportId}/document-jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentJobResponse create(
            @PathVariable Long reportId,
            @Valid @RequestBody CreateDocumentJobRequest request,
            Authentication authentication
    ) {
        return requestService.request(reportId, request, (UserPrincipal) authentication.getPrincipal());
    }

    @GetMapping("/inspection-reports/{reportId}/document-jobs")
    public List<DocumentJobResponse> listByReport(@PathVariable Long reportId) {
        return documentJobService.listByReport(reportId);
    }

    @GetMapping("/inspection-reports/{reportId}/document-preview")
    public DocumentHtmlPreviewResponse previewHtml(
            @PathVariable Long reportId,
            Authentication authentication
    ) {
        return documentJobService.previewHtml(reportId, (UserPrincipal) authentication.getPrincipal());
    }

    @GetMapping("/document-jobs/{jobId}")
    public DocumentJobResponse get(@PathVariable Long jobId) {
        return documentJobService.get(jobId);
    }
}
