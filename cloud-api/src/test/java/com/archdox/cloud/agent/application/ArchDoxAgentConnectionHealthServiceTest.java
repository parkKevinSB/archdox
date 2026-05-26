package com.archdox.cloud.agent.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.agent.domain.ArchDoxAgent;
import com.archdox.cloud.agent.domain.ArchDoxAgentDeploymentMode;
import com.archdox.cloud.agent.domain.ArchDoxAgentSession;
import com.archdox.cloud.agent.domain.ArchDoxAgentSessionStatus;
import com.archdox.cloud.agent.infra.ArchDoxAgentSessionRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class ArchDoxAgentConnectionHealthServiceTest {
    @Test
    void disconnectsHeartbeatTimedOutSessionsAndMarksAgentOfflineWhenNoActiveSessionRemains() {
        var properties = new ArchDoxAgentConnectionHealthProperties();
        properties.setHeartbeatTimeoutMs(1_000);
        var sessionRepository = mock(ArchDoxAgentSessionRepository.class);
        var registry = mock(ArchDoxAgentSessionRegistry.class);
        var commandService = mock(ArchDoxAgentCommandService.class);
        var events = mock(OperationEventService.class);
        var service = new ArchDoxAgentConnectionHealthService(
                properties,
                sessionRepository,
                registry,
                commandService,
                events);
        var now = OffsetDateTime.now();
        var agent = new ArchDoxAgent(
                10L,
                "office-agent",
                ArchDoxAgentDeploymentMode.LOCAL_OFFICE,
                "test",
                Map.of(),
                Map.of(),
                now.minusMinutes(5));
        ReflectionTestUtils.setField(agent, "id", 77L);
        var session = new ArchDoxAgentSession(
                agent,
                "cloud-api-a",
                "ws-1",
                now.minusMinutes(5));
        ReflectionTestUtils.setField(session, "id", 88L);
        when(sessionRepository.findByStatusAndLastSeenAtBeforeOrderByLastSeenAtAsc(
                eq(ArchDoxAgentSessionStatus.ACTIVE),
                any(OffsetDateTime.class),
                any(Pageable.class)))
                .thenReturn(List.of(session));
        when(sessionRepository.existsByAgentIdAndStatus(77L, ArchDoxAgentSessionStatus.ACTIVE))
                .thenReturn(false);

        var disconnected = service.disconnectHeartbeatTimedOutSessions();

        assertEquals(1, disconnected);
        assertEquals(ArchDoxAgentSessionStatus.DISCONNECTED, session.status());
        verify(registry).closeLocalSession(77L, "ws-1", ArchDoxAgentConnectionHealthService.HEARTBEAT_TIMEOUT_REASON);
        verify(commandService).disconnect(77L);
        verify(events).record(
                eq(10L),
                eq(OperationEventSeverity.WARN),
                eq("AGENT_HEARTBEAT_TIMEOUT"),
                eq("agent-connection"),
                eq("agent:77"),
                eq("ARCHDOX_AGENT"),
                eq(77L),
                eq("ArchDox Agent heartbeat timed out; session was marked disconnected."),
                any());
    }
}
