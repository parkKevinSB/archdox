package com.archdox.cloud.agent.api;

import com.archdox.cloud.agent.application.ArchDoxAgentAuthenticationService;
import com.archdox.cloud.document.application.AgentDocumentDeliveryUpload;
import com.archdox.cloud.document.application.DocumentDeliveryService;
import com.archdox.cloud.global.api.BadRequestException;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/agent/api/v1/document-delivery-requests")
public class AgentDocumentDeliveryController {
    private final ArchDoxAgentAuthenticationService authenticationService;
    private final DocumentDeliveryService deliveryService;

    public AgentDocumentDeliveryController(
            ArchDoxAgentAuthenticationService authenticationService,
            DocumentDeliveryService deliveryService
    ) {
        this.authenticationService = authenticationService;
        this.deliveryService = deliveryService;
    }

    @PutMapping(
            value = "/{deliveryRequestId}/content",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AgentDocumentDeliveryUpload uploadPreparedDelivery(
            @PathVariable Long deliveryRequestId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(name = "X-Agent-Token", required = false) String token,
            @RequestHeader(name = "X-Agent-Id", required = false) Long agentId,
            @RequestHeader(name = "X-Agent-Device-Secret", required = false) String deviceSecret,
            @RequestHeader(name = "X-Agent-Office-Id", required = false) String officeHeader
    ) throws IOException {
        var officeId = parseOfficeId(officeHeader);
        authenticationService.authenticateDownload(agentId, deviceSecret, token, officeId);
        return deliveryService.receiveAgentDeliveryUpload(deliveryRequestId, officeId, file.getInputStream());
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
