package com.archdox.cloud.agent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArchDoxAgentSessionWorkflowTest {
    @Test
    void sessionMovesFromActiveToDisconnected() {
        var connectedAt = OffsetDateTime.now();
        var agent = new ArchDoxAgent(10L, "agent-a", "0.0.1", Map.of("documentRender", true), connectedAt);
        var session = new ArchDoxAgentSession(agent, "api-1", "ws-1", connectedAt);

        session.touch(connectedAt.plusSeconds(10));
        session.disconnect("NORMAL", connectedAt.plusSeconds(20));

        assertEquals(10L, session.officeId());
        assertEquals("api-1", session.apiInstanceId());
        assertEquals("ws-1", session.websocketSessionId());
        assertEquals(ArchDoxAgentSessionStatus.DISCONNECTED, session.status());
        assertEquals(connectedAt.plusSeconds(20), session.lastSeenAt());
        assertNotNull(session.disconnectedAt());
    }
}
