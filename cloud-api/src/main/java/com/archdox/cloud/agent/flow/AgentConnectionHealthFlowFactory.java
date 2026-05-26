package com.archdox.cloud.agent.flow;

import com.archdox.cloud.agent.application.ArchDoxAgentConnectionHealthProperties;
import com.archdox.cloud.agent.application.ArchDoxAgentConnectionHealthService;
import com.archdox.cloud.agent.flow.step.AgentConnectionHealthMonitorStep;
import io.github.parkkevinsb.flower.core.flow.Flow;
import org.springframework.stereotype.Component;

@Component
public class AgentConnectionHealthFlowFactory {
    public static final String FLOW_TYPE = "agent-connection-health-monitor";

    private final ArchDoxAgentConnectionHealthService healthService;
    private final ArchDoxAgentConnectionHealthProperties properties;

    public AgentConnectionHealthFlowFactory(
            ArchDoxAgentConnectionHealthService healthService,
            ArchDoxAgentConnectionHealthProperties properties
    ) {
        this.healthService = healthService;
        this.properties = properties;
    }

    public Flow create() {
        return Flow.builder(FLOW_TYPE, "global")
                .step("monitor-heartbeats", new AgentConnectionHealthMonitorStep(
                        healthService,
                        properties))
                .build();
    }
}
