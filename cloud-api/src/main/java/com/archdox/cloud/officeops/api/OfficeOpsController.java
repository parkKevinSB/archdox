package com.archdox.cloud.officeops.api;

import com.archdox.cloud.agent.domain.ArchDoxAgentCommandStatus;
import com.archdox.cloud.document.domain.DocumentDeliveryStatus;
import com.archdox.cloud.document.domain.DocumentJobStatus;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.officeops.application.OfficeOpsReadService;
import com.archdox.cloud.officeops.dto.AgentCommandOpsResponse;
import com.archdox.cloud.officeops.dto.AgentOpsResponse;
import com.archdox.cloud.officeops.dto.AgentSessionOpsResponse;
import com.archdox.cloud.officeops.dto.DocumentDeliveryOpsResponse;
import com.archdox.cloud.officeops.dto.DocumentJobOpsResponse;
import com.archdox.cloud.officeops.dto.OfficeOpsSummaryResponse;
import com.archdox.cloud.officeops.dto.PhotoOpsResponse;
import com.archdox.cloud.photo.domain.PhotoPickupStatus;
import com.archdox.cloud.photo.domain.PhotoStatus;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/office-ops")
public class OfficeOpsController {
    private final OfficeOpsReadService service;

    public OfficeOpsController(OfficeOpsReadService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public OfficeOpsSummaryResponse summary(Authentication authentication) {
        return service.getSummary(principal(authentication));
    }

    @GetMapping("/agents")
    public List<AgentOpsResponse> agents(
            Authentication authentication,
            @RequestParam(required = false) Integer limit
    ) {
        return service.listAgents(principal(authentication), limit);
    }

    @GetMapping("/agent-sessions")
    public List<AgentSessionOpsResponse> agentSessions(
            Authentication authentication,
            @RequestParam(required = false) Integer limit
    ) {
        return service.listAgentSessions(principal(authentication), limit);
    }

    @GetMapping("/agent-commands")
    public List<AgentCommandOpsResponse> agentCommands(
            Authentication authentication,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) ArchDoxAgentCommandStatus status,
            @RequestParam(required = false) Integer limit
    ) {
        return service.listAgentCommands(principal(authentication), agentId, status, limit);
    }

    @GetMapping("/document-jobs")
    public List<DocumentJobOpsResponse> documentJobs(
            Authentication authentication,
            @RequestParam(required = false) DocumentJobStatus status,
            @RequestParam(required = false) Integer limit
    ) {
        return service.listDocumentJobs(principal(authentication), status, limit);
    }

    @GetMapping("/photos")
    public List<PhotoOpsResponse> photos(
            Authentication authentication,
            @RequestParam(required = false) PhotoStatus status,
            @RequestParam(required = false) PhotoPickupStatus originalPickupStatus,
            @RequestParam(required = false) Integer limit
    ) {
        return service.listPhotos(principal(authentication), status, originalPickupStatus, limit);
    }

    @GetMapping("/document-deliveries")
    public List<DocumentDeliveryOpsResponse> documentDeliveries(
            Authentication authentication,
            @RequestParam(required = false) DocumentDeliveryStatus status,
            @RequestParam(required = false) Integer limit
    ) {
        return service.listDeliveries(principal(authentication), status, limit);
    }

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
