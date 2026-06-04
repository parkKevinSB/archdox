package com.archdox.cloud.engine.auth.api;

import com.archdox.cloud.engine.auth.application.EngineApiKeyManagementService;
import com.archdox.cloud.engine.auth.dto.CreateEngineApiKeyRequest;
import com.archdox.cloud.engine.auth.dto.CreateEngineApiKeyResponse;
import com.archdox.cloud.engine.auth.dto.EngineApiKeyResponse;
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
@RequestMapping("/api/v1/platform-admin/engine/api-keys")
public class PlatformEngineApiKeyController {
    private final EngineApiKeyManagementService service;

    public PlatformEngineApiKeyController(EngineApiKeyManagementService service) {
        this.service = service;
    }

    @GetMapping
    public List<EngineApiKeyResponse> keys(Authentication authentication) {
        return service.keys(principal(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateEngineApiKeyResponse create(
            Authentication authentication,
            @Valid @RequestBody CreateEngineApiKeyRequest request
    ) {
        return service.create(principal(authentication), request);
    }

    @PostMapping("/{apiKeyId}/revoke")
    public EngineApiKeyResponse revoke(
            Authentication authentication,
            @PathVariable Long apiKeyId
    ) {
        return service.revoke(principal(authentication), apiKeyId);
    }

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
