package com.archdox.cloud.agent.api;

import com.archdox.cloud.agent.application.ArchDoxAgentLauncherManifestService;
import com.archdox.cloud.agent.dto.ArchDoxAgentLauncherManifestResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/archdox-agents/launcher-manifest")
public class ArchDoxAgentLauncherManifestController {
    private final ArchDoxAgentLauncherManifestService service;

    public ArchDoxAgentLauncherManifestController(ArchDoxAgentLauncherManifestService service) {
        this.service = service;
    }

    @GetMapping
    public ArchDoxAgentLauncherManifestResponse manifest(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String platform
    ) {
        return service.manifest(channel, platform);
    }
}
