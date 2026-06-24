package com.archdox.cloud.platformops.flow;

import com.archdox.cloud.platformops.application.PlatformOpsDetectionMonitorService;
import com.archdox.cloud.platformops.flow.step.PlatformOpsDetectionMonitorStep;
import io.github.parkkevinsb.flower.core.flow.Flow;
import org.springframework.stereotype.Component;

@Component
public class PlatformOpsDetectionMonitorFlowFactory {
    public static final String FLOW_TYPE = "platform-ops-detection-monitor";

    private final PlatformOpsDetectionMonitorService monitorService;
    private final PlatformOpsDetectionFlowFactory detectionFlowFactory;
    private final PlatformOpsWorker platformOpsWorker;

    public PlatformOpsDetectionMonitorFlowFactory(
            PlatformOpsDetectionMonitorService monitorService,
            PlatformOpsDetectionFlowFactory detectionFlowFactory,
            PlatformOpsWorker platformOpsWorker
    ) {
        this.monitorService = monitorService;
        this.detectionFlowFactory = detectionFlowFactory;
        this.platformOpsWorker = platformOpsWorker;
    }

    public Flow create() {
        return Flow.builder(FLOW_TYPE, "global")
                .step("monitor-platform-ops-detection", new PlatformOpsDetectionMonitorStep(
                        monitorService,
                        detectionFlowFactory,
                        platformOpsWorker))
                .build();
    }
}
