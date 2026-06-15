package com.archdox.cloud.engine.mcp;

import com.archdox.cloud.engine.mcp.dto.McpToolCatalogResponse;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin/engine/mcp-tools")
public class PlatformMcpToolCatalogController {
    private final McpToolService toolService;
    private final PlatformAdminService platformAdminService;

    public PlatformMcpToolCatalogController(
            McpToolService toolService,
            PlatformAdminService platformAdminService
    ) {
        this.toolService = toolService;
        this.platformAdminService = platformAdminService;
    }

    @GetMapping
    public List<McpToolCatalogResponse> tools(Authentication authentication) {
        platformAdminService.requirePlatformAdmin((UserPrincipal) authentication.getPrincipal());
        return toolService.toolCatalog();
    }
}
