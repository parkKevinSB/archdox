package com.archdox.cloud.document.api;

import com.archdox.cloud.document.application.DocumentJobService;
import com.archdox.cloud.document.dto.CreateDocumentJobRequest;
import com.archdox.cloud.document.dto.DocumentJobResponse;
import com.archdox.cloud.document.event.DocumentGenerationRequested;
import com.archdox.cloud.document.flow.DocumentGenerationFlowFactory;
import com.archdox.cloud.document.flow.DocumentGenerationWorker;
import com.archdox.cloud.global.security.UserPrincipal;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
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
    private final DocumentGenerationFlowFactory flowFactory;
    private final DocumentGenerationWorker worker;

    public DocumentJobController(
            DocumentJobService documentJobService,
            DocumentGenerationFlowFactory flowFactory,
            DocumentGenerationWorker worker
    ) {
        this.documentJobService = documentJobService;
        this.flowFactory = flowFactory;
        this.worker = worker;
    }

    @PostMapping("/inspection-reports/{reportId}/document-jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentJobResponse create(
            @PathVariable Long reportId,
            @Valid @RequestBody CreateDocumentJobRequest request,
            Authentication authentication
    ) {
        var response = documentJobService.create(reportId, request, (UserPrincipal) authentication.getPrincipal());
        worker.submit(flowFactory.create(new DocumentGenerationRequested(
                response.officeId(),
                response.reportId(),
                response.id(),
                response.workerType(),
                OffsetDateTime.now())));
        return response;
    }

    @GetMapping("/inspection-reports/{reportId}/document-jobs")
    public List<DocumentJobResponse> listByReport(@PathVariable Long reportId) {
        return documentJobService.listByReport(reportId);
    }

    @GetMapping("/document-jobs/{jobId}")
    public DocumentJobResponse get(@PathVariable Long jobId) {
        return documentJobService.get(jobId);
    }
}
