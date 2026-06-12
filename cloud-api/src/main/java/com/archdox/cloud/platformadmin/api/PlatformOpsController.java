package com.archdox.cloud.platformadmin.api;

import com.archdox.cloud.agent.domain.ArchDoxAgentCommandStatus;
import com.archdox.cloud.document.domain.DocumentDeliveryStatus;
import com.archdox.cloud.document.domain.DocumentJobStatus;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.monitoring.dto.PlatformServerRuntimeHealthResponse;
import com.archdox.cloud.monitoring.dto.ServerRuntimeHealthSettingsResponse;
import com.archdox.cloud.monitoring.dto.UpdateServerRuntimeHealthSettingsRequest;
import com.archdox.cloud.operation.dto.OperationEventResponse;
import com.archdox.cloud.photo.domain.PhotoPickupStatus;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.platformadmin.application.PlatformOpsReadService;
import com.archdox.cloud.platformadmin.application.PlatformHealthDetectionService;
import com.archdox.cloud.platformadmin.dto.PlatformAgentCommandOpsResponse;
import com.archdox.cloud.platformadmin.dto.PlatformAgentOpsResponse;
import com.archdox.cloud.platformadmin.dto.PlatformDeliveryOpsResponse;
import com.archdox.cloud.platformadmin.dto.PlatformDocumentJobOpsResponse;
import com.archdox.cloud.platformadmin.dto.PlatformOfficeOpsResponse;
import com.archdox.cloud.platformadmin.dto.PlatformHealthDetectionResponse;
import com.archdox.cloud.platformadmin.dto.PlatformOpsSummaryResponse;
import com.archdox.cloud.platformadmin.dto.PlatformPhotoOpsResponse;
import com.archdox.cloud.platformadmin.dto.PlatformUserOpsResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin/ops")
public class PlatformOpsController {
    private final PlatformOpsReadService service;
    private final PlatformHealthDetectionService healthDetectionService;

    public PlatformOpsController(PlatformOpsReadService service, PlatformHealthDetectionService healthDetectionService) {
        this.service = service;
        this.healthDetectionService = healthDetectionService;
    }

    @GetMapping("/summary")
    public PlatformOpsSummaryResponse summary(Authentication authentication) {
        return service.summary(principal(authentication));
    }

    @GetMapping("/server-runtime")
    public PlatformServerRuntimeHealthResponse serverRuntime(Authentication authentication) {
        return service.serverRuntime(principal(authentication));
    }

    @PutMapping("/server-runtime/settings")
    public ServerRuntimeHealthSettingsResponse updateServerRuntimeSettings(
            Authentication authentication,
            @RequestBody UpdateServerRuntimeHealthSettingsRequest request
    ) {
        return service.updateServerRuntimeSettings(principal(authentication), request);
    }

    @GetMapping("/users")
    public List<PlatformUserOpsResponse> users(Authentication authentication, @RequestParam(required = false) Integer limit) {
        return service.users(principal(authentication), limit);
    }

    @GetMapping("/offices")
    public List<PlatformOfficeOpsResponse> offices(Authentication authentication, @RequestParam(required = false) Integer limit) {
        return service.offices(principal(authentication), limit);
    }

    @GetMapping("/agents")
    public List<PlatformAgentOpsResponse> agents(Authentication authentication, @RequestParam(required = false) Integer limit) {
        return service.agents(principal(authentication), limit);
    }

    @GetMapping("/agent-commands")
    public List<PlatformAgentCommandOpsResponse> commands(
            Authentication authentication,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) ArchDoxAgentCommandStatus status,
            @RequestParam(required = false) Integer limit
    ) {
        return service.commands(principal(authentication), officeId, agentId, status, limit);
    }

    @GetMapping("/document-jobs")
    public List<PlatformDocumentJobOpsResponse> documentJobs(
            Authentication authentication,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) DocumentJobStatus status,
            @RequestParam(required = false) Integer limit
    ) {
        return service.documentJobs(principal(authentication), officeId, status, limit);
    }

    @GetMapping("/photos")
    public List<PlatformPhotoOpsResponse> photos(
            Authentication authentication,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) PhotoStatus status,
            @RequestParam(required = false) PhotoPickupStatus pickupStatus,
            @RequestParam(required = false) Integer limit
    ) {
        return service.photos(principal(authentication), officeId, status, pickupStatus, limit);
    }

    @GetMapping("/deliveries")
    public List<PlatformDeliveryOpsResponse> deliveries(
            Authentication authentication,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) DocumentDeliveryStatus status,
            @RequestParam(required = false) Integer limit
    ) {
        return service.deliveries(principal(authentication), officeId, status, limit);
    }

    @GetMapping("/events")
    public List<OperationEventResponse> events(
            Authentication authentication,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String workflowType,
            @RequestParam(required = false) String workflowKey,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) Integer limit
    ) {
        return service.events(
                principal(authentication),
                officeId,
                eventType,
                workflowType,
                workflowKey,
                resourceType,
                resourceId,
                limit);
    }

    @PostMapping("/health/detect-stuck")
    public PlatformHealthDetectionResponse detectStuck(Authentication authentication) {
        return healthDetectionService.detectAndRecord(principal(authentication));
    }

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
