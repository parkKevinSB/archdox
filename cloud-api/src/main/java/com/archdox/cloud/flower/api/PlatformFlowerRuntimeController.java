package com.archdox.cloud.flower.api;

import com.archdox.cloud.flower.application.FlowerRuntimeReadService;
import com.archdox.cloud.flower.dto.FlowerRuntimeDumpResponse;
import com.archdox.cloud.global.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin/flower")
public class PlatformFlowerRuntimeController {
    private final FlowerRuntimeReadService service;

    public PlatformFlowerRuntimeController(FlowerRuntimeReadService service) {
        this.service = service;
    }

    @GetMapping("/dump")
    public FlowerRuntimeDumpResponse dump(Authentication authentication) {
        return service.dump(principal(authentication));
    }

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
