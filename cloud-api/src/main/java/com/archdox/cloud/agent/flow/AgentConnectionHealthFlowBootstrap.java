package com.archdox.cloud.agent.flow;

import com.archdox.cloud.agent.application.ArchDoxAgentConnectionHealthProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AgentConnectionHealthFlowBootstrap {
    private final ArchDoxAgentConnectionHealthProperties properties;
    private final AgentConnectionHealthFlowFactory flowFactory;
    private final AgentConnectionHealthWorker worker;

    public AgentConnectionHealthFlowBootstrap(
            ArchDoxAgentConnectionHealthProperties properties,
            AgentConnectionHealthFlowFactory flowFactory,
            AgentConnectionHealthWorker worker
    ) {
        this.properties = properties;
        this.flowFactory = flowFactory;
        this.worker = worker;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void submitAgentConnectionHealthFlow() {
        if (!properties.isEnabled()) {
            return;
        }
        worker.submit(flowFactory.create());
    }
}
