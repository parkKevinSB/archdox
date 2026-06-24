package com.archdox.cloud.platformops.flow;

import com.archdox.cloud.platformops.application.PlatformOpsRetentionService;
import com.archdox.cloud.platformops.flow.step.PlatformOpsRetentionMonitorStep;
import io.github.parkkevinsb.flower.core.flow.Flow;
import org.springframework.stereotype.Component;

@Component
public class PlatformOpsRetentionMonitorFlowFactory {
    public static final String FLOW_TYPE = "platform-ops-retention-monitor";

    private final PlatformOpsRetentionService retentionService;

    public PlatformOpsRetentionMonitorFlowFactory(PlatformOpsRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    public Flow create() {
        return Flow.builder(FLOW_TYPE, "global")
                .step("monitor-platform-ops-retention", new PlatformOpsRetentionMonitorStep(retentionService))
                .build();
    }
}
