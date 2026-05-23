package com.archdox.cloud.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "archdox_agent_sessions")
public class ArchDoxAgentSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private ArchDoxAgent agent;

    @Column(name = "api_instance_id", nullable = false)
    private String apiInstanceId;

    @Column(name = "websocket_session_id", nullable = false)
    private String websocketSessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArchDoxAgentSessionStatus status;

    @Column(name = "connected_at", nullable = false)
    private OffsetDateTime connectedAt;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    @Column(name = "disconnected_at")
    private OffsetDateTime disconnectedAt;

    @Column(name = "disconnect_reason")
    private String disconnectReason;

    protected ArchDoxAgentSession() {
    }

    public ArchDoxAgentSession(
            ArchDoxAgent agent,
            String apiInstanceId,
            String websocketSessionId,
            OffsetDateTime now
    ) {
        this.officeId = agent.officeId();
        this.agent = agent;
        this.apiInstanceId = apiInstanceId;
        this.websocketSessionId = websocketSessionId;
        this.status = ArchDoxAgentSessionStatus.ACTIVE;
        this.connectedAt = now;
        this.lastSeenAt = now;
    }

    public void touch(OffsetDateTime now) {
        this.lastSeenAt = now;
    }

    public void disconnect(String reason, OffsetDateTime now) {
        if (status == ArchDoxAgentSessionStatus.DISCONNECTED) {
            return;
        }
        this.status = ArchDoxAgentSessionStatus.DISCONNECTED;
        this.lastSeenAt = now;
        this.disconnectedAt = now;
        this.disconnectReason = reason;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public ArchDoxAgent agent() {
        return agent;
    }

    public String apiInstanceId() {
        return apiInstanceId;
    }

    public String websocketSessionId() {
        return websocketSessionId;
    }

    public ArchDoxAgentSessionStatus status() {
        return status;
    }

    public OffsetDateTime connectedAt() {
        return connectedAt;
    }

    public OffsetDateTime lastSeenAt() {
        return lastSeenAt;
    }

    public OffsetDateTime disconnectedAt() {
        return disconnectedAt;
    }

    public String disconnectReason() {
        return disconnectReason;
    }
}
