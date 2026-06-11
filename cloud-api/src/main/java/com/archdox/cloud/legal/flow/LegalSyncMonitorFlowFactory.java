package com.archdox.cloud.legal.flow;

import com.archdox.cloud.legal.application.LegalSyncMonitorService;
import com.archdox.cloud.legal.application.LegalSyncProperties;
import com.archdox.cloud.legal.flow.step.LegalSyncMonitorStep;
import io.github.parkkevinsb.flower.core.flow.Flow;
import org.springframework.stereotype.Component;

@Component
public class LegalSyncMonitorFlowFactory {
    public static final String FLOW_TYPE = "legal-sync-monitor";

    private final LegalSyncMonitorService monitorService;
    private final LegalSyncProperties properties;

    public LegalSyncMonitorFlowFactory(
            LegalSyncMonitorService monitorService,
            LegalSyncProperties properties
    ) {
        this.monitorService = monitorService;
        this.properties = properties;
    }

    public Flow create() {
        return Flow.builder(FLOW_TYPE, "global")
                .step("monitor-legal-open-api-sync", new LegalSyncMonitorStep(monitorService, properties))
                .build();
    }
}
