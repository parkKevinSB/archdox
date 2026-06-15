package com.archdox.cloud.engine.auth.api;

import com.archdox.cloud.engine.auth.application.EngineApiKeyManagementService;
import com.archdox.cloud.engine.auth.dto.EngineApiKeyResponse;
import com.archdox.cloud.global.security.UserPrincipal;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/engine/api-keys")
public class EngineApiKeyController {
    private final EngineApiKeyManagementService service;

    public EngineApiKeyController(EngineApiKeyManagementService service) {
        this.service = service;
    }

    @GetMapping
    public List<EngineApiKeyResponse> keys(Authentication authentication) {
        return service.userKeys(principal(authentication));
    }

    @PostMapping("/{apiKeyId}/revoke")
    public EngineApiKeyResponse revoke(
            Authentication authentication,
            @PathVariable Long apiKeyId
    ) {
        return service.revokeOwn(principal(authentication), apiKeyId);
    }

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
