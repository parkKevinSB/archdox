package com.archdox.cloud.monitoring.flow;

import com.archdox.cloud.monitoring.application.ServerRuntimeHealthService;
import com.archdox.cloud.monitoring.flow.step.ServerRuntimeHealthMonitorStep;
import io.github.parkkevinsb.flower.core.flow.Flow;
import org.springframework.stereotype.Component;

@Component
public class ServerRuntimeHealthMonitorFlowFactory {
    public static final String FLOW_TYPE = "server-runtime-health-monitor";

    private final ServerRuntimeHealthService healthService;

    public ServerRuntimeHealthMonitorFlowFactory(ServerRuntimeHealthService healthService) {
        this.healthService = healthService;
    }

    public Flow create() {
        return Flow.builder(FLOW_TYPE, "global")
                .step("monitor-server-runtime-health", new ServerRuntimeHealthMonitorStep(healthService))
                .build();
    }
}
