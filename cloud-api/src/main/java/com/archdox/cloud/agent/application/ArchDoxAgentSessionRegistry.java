package com.archdox.cloud.agent.application;

import com.archdox.cloud.agent.domain.ArchDoxAgent;
import com.archdox.cloud.agent.domain.ArchDoxAgentSession;
import com.archdox.cloud.agent.domain.ArchDoxAgentSessionStatus;
import com.archdox.cloud.agent.infra.ArchDoxAgentSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class ArchDoxAgentSessionRegistry {
    private final ObjectMapper objectMapper;
    private final ArchDoxAgentSessionRepository sessionRepository;
    private final ArchDoxAgentProperties properties;
    private final Map<Long, Map<String, WebSocketSession>> sessionsByAgentId = new ConcurrentHashMap<>();
    private final Map<String, RegisteredAgentSession> sessionRefsByWebSocketId = new ConcurrentHashMap<>();

    public ArchDoxAgentSessionRegistry(
            ObjectMapper objectMapper,
            ArchDoxAgentSessionRepository sessionRepository,
            ArchDoxAgentProperties properties
    ) {
        this.objectMapper = objectMapper;
        this.sessionRepository = sessionRepository;
        this.properties = properties;
    }

    @Transactional
    public void register(ArchDoxAgent agent, WebSocketSession session) {
        var now = OffsetDateTime.now();
        var agentSession = sessionRepository.save(new ArchDoxAgentSession(
                agent,
                properties.getApiInstanceId(),
                session.getId(),
                now));
        sessionsByAgentId
                .computeIfAbsent(agent.id(), ignored -> new ConcurrentHashMap<>())
                .put(session.getId(), session);
        sessionRefsByWebSocketId.put(
                session.getId(),
                new RegisteredAgentSession(agent.id(), agentSession.id()));
    }

    @Transactional
    public Long unregister(WebSocketSession session, String reason) {
        var sessionRef = sessionRefsByWebSocketId.remove(session.getId());
        if (sessionRef != null) {
            removeLocalSession(sessionRef.agentId(), session.getId());
            sessionRepository.findById(sessionRef.agentSessionId())
                    .ifPresent(agentSession -> agentSession.disconnect(reason, OffsetDateTime.now()));
            return sessionRef.agentId();
        }
        return sessionRepository
                .findByApiInstanceIdAndWebsocketSessionId(properties.getApiInstanceId(), session.getId())
                .map(agentSession -> {
                    agentSession.disconnect(reason, OffsetDateTime.now());
                    return agentSession.agent().id();
                })
                .orElse(null);
    }

    public Long agentId(WebSocketSession session) {
        var sessionRef = sessionRefsByWebSocketId.get(session.getId());
        return sessionRef == null ? null : sessionRef.agentId();
    }

    @Transactional
    public Long touch(WebSocketSession session) {
        var sessionRef = sessionRefsByWebSocketId.get(session.getId());
        if (sessionRef == null) {
            return null;
        }
        sessionRepository.findById(sessionRef.agentSessionId())
                .ifPresent(agentSession -> agentSession.touch(OffsetDateTime.now()));
        return sessionRef.agentId();
    }

    public boolean send(Long agentId, AgentOutboundMessage message) {
        var sessions = sessionsByAgentId.get(agentId);
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }
        for (var session : sessions.values()) {
            if (session == null || !session.isOpen()) {
                continue;
            }
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
                return true;
            } catch (IOException ex) {
                // Try another active local socket for the same logical agent.
            }
        }
        return false;
    }

    public boolean closeLocalSession(Long agentId, String websocketSessionId, String reason) {
        if (agentId == null || websocketSessionId == null || websocketSessionId.isBlank()) {
            return false;
        }
        var sessions = sessionsByAgentId.get(agentId);
        if (sessions == null) {
            return false;
        }
        var session = sessions.remove(websocketSessionId);
        if (sessions.isEmpty()) {
            sessionsByAgentId.remove(agentId);
        }
        sessionRefsByWebSocketId.remove(websocketSessionId);
        if (session == null || !session.isOpen()) {
            return false;
        }
        try {
            session.close();
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private void removeLocalSession(Long agentId, String websocketSessionId) {
        var sessions = sessionsByAgentId.get(agentId);
        if (sessions == null) {
            return;
        }
        sessions.remove(websocketSessionId);
        if (sessions.isEmpty()) {
            sessionsByAgentId.remove(agentId);
        }
    }

    private record RegisteredAgentSession(Long agentId, Long agentSessionId) {
    }
}
