package com.archdox.cloud.system.api;

import com.archdox.cloud.system.application.ArchDoxCloudBuildInfoService;
import com.archdox.cloud.system.dto.ArchDoxSystemVersionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/version")
public class ArchDoxSystemVersionController {
    private final ArchDoxCloudBuildInfoService buildInfoService;

    public ArchDoxSystemVersionController(ArchDoxCloudBuildInfoService buildInfoService) {
        this.buildInfoService = buildInfoService;
    }

    @GetMapping
    public ArchDoxSystemVersionResponse version() {
        var build = buildInfoService.current();
        return new ArchDoxSystemVersionResponse(
                build.module(),
                build.version(),
                build.gitCommit(),
                build.gitBranch(),
                build.buildTime());
    }
}
