package com.archdox.cloud.aiharness.flow;

import com.archdox.cloud.aiharness.application.AiObservabilityRetentionService;
import com.archdox.cloud.aiharness.flow.step.AiObservabilityRetentionMonitorStep;
import io.github.parkkevinsb.flower.core.flow.Flow;
import org.springframework.stereotype.Component;

@Component
public class AiObservabilityRetentionMonitorFlowFactory {
    public static final String FLOW_TYPE = "ai-observability-retention-monitor";

    private final AiObservabilityRetentionService retentionService;

    public AiObservabilityRetentionMonitorFlowFactory(AiObservabilityRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    public Flow create() {
        return Flow.builder(FLOW_TYPE, "global")
                .step("monitor-ai-observability-retention", new AiObservabilityRetentionMonitorStep(retentionService))
                .build();
    }
}
