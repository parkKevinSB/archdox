package com.archdox.cloud.document.api;

import com.archdox.cloud.document.application.DocumentDeliveryService;
import com.archdox.cloud.document.dto.CreateDocumentDeliveryRequest;
import com.archdox.cloud.document.dto.DocumentDeliveryRequestResponse;
import com.archdox.cloud.document.event.DocumentDeliveryRequested;
import com.archdox.cloud.document.flow.DocumentDeliveryFlowFactory;
import com.archdox.cloud.document.flow.DocumentDeliveryWorker;
import com.archdox.cloud.global.security.UserPrincipal;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1")
public class DocumentDeliveryController {
    private final DocumentDeliveryService deliveryService;
    private final DocumentDeliveryFlowFactory deliveryFlowFactory;
    private final DocumentDeliveryWorker deliveryWorker;

    public DocumentDeliveryController(
            DocumentDeliveryService deliveryService,
            DocumentDeliveryFlowFactory deliveryFlowFactory,
            DocumentDeliveryWorker deliveryWorker
    ) {
        this.deliveryService = deliveryService;
        this.deliveryFlowFactory = deliveryFlowFactory;
        this.deliveryWorker = deliveryWorker;
    }

    @PostMapping("/document-jobs/{jobId}/delivery-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDeliveryRequestResponse create(
            @PathVariable Long jobId,
            @Valid @RequestBody(required = false) CreateDocumentDeliveryRequest request,
            Authentication authentication
    ) {
        var response = deliveryService.create(jobId, request, (UserPrincipal) authentication.getPrincipal());
        if (deliveryService.requiresAgentUpload(response.id())) {
            deliveryWorker.submit(deliveryFlowFactory.create(new DocumentDeliveryRequested(
                    response.officeId(),
                    response.documentJobId(),
                    response.id(),
                    response.artifactId(),
                    response.requestedAt())));
        }
        return response;
    }

    @GetMapping("/document-jobs/{jobId}/delivery-requests")
    public List<DocumentDeliveryRequestResponse> listByJob(@PathVariable Long jobId) {
        return deliveryService.listByJob(jobId);
    }

    @GetMapping("/document-delivery-requests/{deliveryRequestId}")
    public DocumentDeliveryRequestResponse get(@PathVariable Long deliveryRequestId) {
        return deliveryService.get(deliveryRequestId);
    }

    @GetMapping("/document-delivery-requests/{deliveryRequestId}/download")
    public ResponseEntity<StreamingResponseBody> downloadPreparedDelivery(@PathVariable Long deliveryRequestId) throws IOException {
        var download = deliveryService.prepareDeliveryDownload(deliveryRequestId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.mimeType()))
                .contentLength(download.bytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(download.fileName()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(download.body());
    }

    @GetMapping("/document-artifacts/{artifactId}/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable Long artifactId) throws IOException {
        var download = deliveryService.prepareDownload(artifactId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.mimeType()))
                .contentLength(download.bytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(download.fileName()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(download.body());
    }

    private String contentDisposition(String fileName) {
        return ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString()
                + "; filename*=UTF-8''"
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
