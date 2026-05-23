package com.archdox.cloud.agent.application;

import com.archdox.cloud.agent.domain.ArchDoxAgentSessionStatus;
import com.archdox.cloud.agent.infra.ArchDoxAgentSessionRepository;
import java.time.OffsetDateTime;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ArchDoxAgentSessionLifecycle {
    private final ArchDoxAgentSessionRepository sessionRepository;
    private final ArchDoxAgentProperties properties;

    public ArchDoxAgentSessionLifecycle(
            ArchDoxAgentSessionRepository sessionRepository,
            ArchDoxAgentProperties properties
    ) {
        this.sessionRepository = sessionRepository;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void disconnectStaleSessionsForThisApiInstance() {
        sessionRepository.markActiveSessionsDisconnectedForApiInstance(
                properties.getApiInstanceId(),
                ArchDoxAgentSessionStatus.ACTIVE,
                ArchDoxAgentSessionStatus.DISCONNECTED,
                OffsetDateTime.now(),
                "API instance started");
    }
}
