package com.archdox.cloud.agent.api;

import com.archdox.cloud.agent.application.ArchDoxAgentAuthenticationService;
import com.archdox.cloud.document.application.DocumentJobService;
import com.archdox.cloud.global.api.BadRequestException;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/agent/api/v1/document-jobs")
public class AgentDocumentTemplateContentController {
    private final ArchDoxAgentAuthenticationService authenticationService;
    private final DocumentJobService documentJobService;

    public AgentDocumentTemplateContentController(
            ArchDoxAgentAuthenticationService authenticationService,
            DocumentJobService documentJobService
    ) {
        this.authenticationService = authenticationService;
        this.documentJobService = documentJobService;
    }

    @GetMapping("/{documentJobId}/render-package")
    public Map<String, Object> renderPackage(
            @PathVariable Long documentJobId,
            @RequestHeader(name = "X-Agent-Token", required = false) String token,
            @RequestHeader(name = "X-Agent-Id", required = false) Long agentId,
            @RequestHeader(name = "X-Agent-Device-Secret", required = false) String deviceSecret,
            @RequestHeader(name = "X-Agent-Office-Id", required = false) String officeHeader
    ) {
        var officeId = parseOfficeId(officeHeader);
        authenticationService.authenticateDownload(agentId, deviceSecret, token, officeId);
        return documentJobService.buildArchDoxAgentRenderPayload(officeId, documentJobId);
    }

    @GetMapping("/{documentJobId}/template/content")
    public ResponseEntity<StreamingResponseBody> downloadTemplateContent(
            @PathVariable Long documentJobId,
            @RequestHeader(name = "X-Agent-Token", required = false) String token,
            @RequestHeader(name = "X-Agent-Id", required = false) Long agentId,
            @RequestHeader(name = "X-Agent-Device-Secret", required = false) String deviceSecret,
            @RequestHeader(name = "X-Agent-Office-Id", required = false) String officeHeader
    ) throws IOException {
        var officeId = parseOfficeId(officeHeader);
        authenticationService.authenticateDownload(agentId, deviceSecret, token, officeId);
        var download = documentJobService.downloadArchDoxAgentTemplate(officeId, documentJobId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.mimeType()))
                .contentLength(download.bytes())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.fileName())
                        .build()
                        .toString())
                .body(download.body());
    }

    private Long parseOfficeId(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("X-Agent-Office-Id is required");
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Invalid X-Agent-Office-Id");
        }
    }
}
