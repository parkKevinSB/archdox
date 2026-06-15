package com.archdox.cloud.engine.mcp;

import com.archdox.cloud.engine.mcp.dto.McpToolCatalogResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/engine/mcp-tools")
public class McpToolCatalogController {
    private final McpToolService toolService;

    public McpToolCatalogController(McpToolService toolService) {
        this.toolService = toolService;
    }

    @GetMapping
    public List<McpToolCatalogResponse> tools() {
        return toolService.toolCatalog();
    }
}
