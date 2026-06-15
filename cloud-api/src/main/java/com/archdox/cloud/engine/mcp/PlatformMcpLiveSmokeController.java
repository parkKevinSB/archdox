package com.archdox.cloud.engine.mcp;

import com.archdox.cloud.engine.mcp.dto.McpLiveSmokeRequest;
import com.archdox.cloud.engine.mcp.dto.McpLiveSmokeResponse;
import com.archdox.cloud.global.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin/engine/mcp-smoke")
public class PlatformMcpLiveSmokeController {
    private final McpLiveSmokeService service;

    public PlatformMcpLiveSmokeController(McpLiveSmokeService service) {
        this.service = service;
    }

    @PostMapping
    public McpLiveSmokeResponse run(
            Authentication authentication,
            @RequestBody McpLiveSmokeRequest request
    ) {
        return service.run((UserPrincipal) authentication.getPrincipal(), request == null ? null : request.apiKey());
    }
}
