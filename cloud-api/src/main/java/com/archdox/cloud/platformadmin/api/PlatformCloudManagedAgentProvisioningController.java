package com.archdox.cloud.platformadmin.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformCloudManagedAgentProvisioningService;
import com.archdox.cloud.platformadmin.dto.ProvisionCloudManagedAgentRequest;
import com.archdox.cloud.platformadmin.dto.ProvisionCloudManagedAgentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin/agents/cloud-managed")
public class PlatformCloudManagedAgentProvisioningController {
    private final PlatformCloudManagedAgentProvisioningService service;

    public PlatformCloudManagedAgentProvisioningController(PlatformCloudManagedAgentProvisioningService service) {
        this.service = service;
    }

    @PostMapping("/provision-device-secret")
    @ResponseStatus(HttpStatus.CREATED)
    public ProvisionCloudManagedAgentResponse provision(
            Authentication authentication,
            @RequestBody ProvisionCloudManagedAgentRequest request
    ) {
        return service.provision((UserPrincipal) authentication.getPrincipal(), request);
    }
}
