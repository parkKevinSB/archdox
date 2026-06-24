package com.archdox.cloud.platformops.application;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ApplicationLogEventDetector implements PlatformOpsDetector {
    private final PlatformOpsLogProjectionService projectionService;

    public ApplicationLogEventDetector(PlatformOpsLogProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @Override
    public String category() {
        return "APPLICATION_LOG";
    }

    @Override
    public List<PlatformOpsDetectionFinding> detect(PlatformOpsDetectionContext context) {
        return projectionService.project(context.now(), context.page());
    }
}
