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
@RequestMapping("/api/v1/engine/mcp-smoke")
public class McpLiveSmokeController {
    private final McpLiveSmokeService service;

    public McpLiveSmokeController(McpLiveSmokeService service) {
        this.service = service;
    }

    @PostMapping
    public McpLiveSmokeResponse run(
            Authentication authentication,
            @RequestBody McpLiveSmokeRequest request
    ) {
        return service.runOwn((UserPrincipal) authentication.getPrincipal(), request == null ? null : request.apiKey());
    }
}
