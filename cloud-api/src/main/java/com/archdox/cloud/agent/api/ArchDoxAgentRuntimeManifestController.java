package com.archdox.cloud.agent.api;

import com.archdox.cloud.agent.application.ArchDoxAgentRuntimeManifestService;
import com.archdox.cloud.agent.dto.ArchDoxAgentRuntimeManifestResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/archdox-agents/runtime-manifest")
public class ArchDoxAgentRuntimeManifestController {
    private final ArchDoxAgentRuntimeManifestService service;

    public ArchDoxAgentRuntimeManifestController(ArchDoxAgentRuntimeManifestService service) {
        this.service = service;
    }

    @GetMapping
    public ArchDoxAgentRuntimeManifestResponse manifest(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String platform
    ) {
        return service.manifest(channel, platform);
    }
}
