package com.archdox.cloud.agent.application;

import com.archdox.cloud.agent.domain.ArchDoxAgentSession;
import com.archdox.cloud.agent.domain.ArchDoxAgentSessionStatus;
import com.archdox.cloud.agent.infra.ArchDoxAgentSessionRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArchDoxAgentConnectionHealthService {
    static final String HEARTBEAT_TIMEOUT_REASON = "Heartbeat timeout";

    private final ArchDoxAgentConnectionHealthProperties properties;
    private final ArchDoxAgentSessionRepository sessionRepository;
    private final ArchDoxAgentSessionRegistry sessionRegistry;
    private final ArchDoxAgentCommandService commandService;
    private final OperationEventService operationEventService;

    public ArchDoxAgentConnectionHealthService(
            ArchDoxAgentConnectionHealthProperties properties,
            ArchDoxAgentSessionRepository sessionRepository,
            ArchDoxAgentSessionRegistry sessionRegistry,
            ArchDoxAgentCommandService commandService,
            OperationEventService operationEventService
    ) {
        this.properties = properties;
        this.sessionRepository = sessionRepository;
        this.sessionRegistry = sessionRegistry;
        this.commandService = commandService;
        this.operationEventService = operationEventService;
    }

    @Transactional
    public int disconnectHeartbeatTimedOutSessions() {
        var now = OffsetDateTime.now();
        var cutoff = now.minus(Duration.ofMillis(properties.safeHeartbeatTimeoutMs()));
        var staleSessions = sessionRepository.findByStatusAndLastSeenAtBeforeOrderByLastSeenAtAsc(
                ArchDoxAgentSessionStatus.ACTIVE,
                cutoff,
                PageRequest.of(0, properties.safeMaxSessionsPerCheck()));
        staleSessions.forEach(session -> disconnectTimedOutSession(session, cutoff, now));
        return staleSessions.size();
    }

    @Transactional
    public int disconnectHeartbeatTimedOutSessions(Long agentId) {
        if (agentId == null) {
            return 0;
        }
        var now = OffsetDateTime.now();
        var cutoff = now.minus(Duration.ofMillis(properties.safeHeartbeatTimeoutMs()));
        var staleSessions = sessionRepository.findByAgentIdAndStatusAndLastSeenAtBeforeOrderByLastSeenAtAsc(
                agentId,
                ArchDoxAgentSessionStatus.ACTIVE,
                cutoff);
        staleSessions.forEach(session -> disconnectTimedOutSession(session, cutoff, now));
        return staleSessions.size();
    }

    @Transactional
    public int pruneDisconnectedSessionHistory() {
        return sessionRepository.deleteDisconnectedSessionsBeyondRecentPerOffice(
                properties.safeRetainedDisconnectedSessionsPerOffice());
    }

    @Transactional(readOnly = true)
    public boolean hasActiveSession(Long agentId) {
        return agentId != null
                && sessionRepository.existsByAgentIdAndStatus(agentId, ArchDoxAgentSessionStatus.ACTIVE);
    }

    private void disconnectTimedOutSession(
            ArchDoxAgentSession session,
            OffsetDateTime cutoff,
            OffsetDateTime now
    ) {
        var agentId = session.agent().id();
        var lastSeenAt = session.lastSeenAt();
        sessionRegistry.closeLocalSession(agentId, session.websocketSessionId(), HEARTBEAT_TIMEOUT_REASON);
        session.disconnect(HEARTBEAT_TIMEOUT_REASON, now);
        if (!sessionRepository.existsByAgentIdAndStatus(agentId, ArchDoxAgentSessionStatus.ACTIVE)) {
            commandService.disconnect(agentId);
        }
        operationEventService.record(
                session.officeId(),
                OperationEventSeverity.WARN,
                "AGENT_HEARTBEAT_TIMEOUT",
                "agent-connection",
                "agent:" + agentId,
                "ARCHDOX_AGENT",
                agentId,
                "ArchDox Agent heartbeat timed out; session was marked disconnected.",
                Map.of(
                        "agentSessionId", session.id(),
                        "agentId", agentId,
                        "apiInstanceId", session.apiInstanceId(),
                        "websocketSessionId", session.websocketSessionId(),
                        "lastSeenAt", lastSeenAt.toString(),
                        "cutoff", cutoff.toString(),
                        "heartbeatTimeoutMs", properties.safeHeartbeatTimeoutMs()));
    }
}
