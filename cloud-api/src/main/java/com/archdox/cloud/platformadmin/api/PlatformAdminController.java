package com.archdox.cloud.platformadmin.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.cloud.platformadmin.dto.PlatformAdminMeResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin")
public class PlatformAdminController {
    private final PlatformAdminService service;

    public PlatformAdminController(PlatformAdminService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public PlatformAdminMeResponse me(Authentication authentication) {
        return service.me((UserPrincipal) authentication.getPrincipal());
    }
}
