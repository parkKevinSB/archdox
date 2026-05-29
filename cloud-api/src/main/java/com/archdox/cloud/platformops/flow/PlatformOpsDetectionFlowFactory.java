package com.archdox.cloud.platformops.flow;

import com.archdox.cloud.platformops.application.PlatformOpsDetectionService;
import com.archdox.cloud.platformops.event.PlatformOpsDetectionRequested;
import com.archdox.cloud.platformops.flow.step.RunPlatformOpsDetectorsStep;
import io.github.parkkevinsb.flower.core.flow.Flow;
import org.springframework.stereotype.Component;

@Component
public class PlatformOpsDetectionFlowFactory {
    public static final String FLOW_TYPE = "platform-ops-detection";

    private final PlatformOpsDetectionService detectionService;

    public PlatformOpsDetectionFlowFactory(PlatformOpsDetectionService detectionService) {
        this.detectionService = detectionService;
    }

    public Flow create(PlatformOpsDetectionRequested event) {
        return Flow.builder(FLOW_TYPE, "run:" + event.opsRunId())
                .step("run-deterministic-detectors", new RunPlatformOpsDetectorsStep(detectionService, event))
                .build();
    }
}
