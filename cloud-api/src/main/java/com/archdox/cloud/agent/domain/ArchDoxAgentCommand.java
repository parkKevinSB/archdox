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
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "archdox_agent_commands")
public class ArchDoxAgentCommand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private ArchDoxAgent agent;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false)
    private ArchDoxAgentCommandType commandType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArchDoxAgentCommandStatus status;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "ack_at")
    private OffsetDateTime ackAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "failed_at")
    private OffsetDateTime failedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private Map<String, Object> resultJson;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    protected ArchDoxAgentCommand() {
    }

    public ArchDoxAgentCommand(ArchDoxAgent agent, ArchDoxAgentCommandType commandType, Map<String, Object> payload, OffsetDateTime now, OffsetDateTime expiresAt) {
        this.agent = agent;
        this.commandType = commandType;
        this.payloadJson = payload;
        this.status = ArchDoxAgentCommandStatus.PENDING;
        this.attemptCount = 0;
        this.maxAttempts = 5;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public void configureRetry(int maxAttempts, OffsetDateTime nextAttemptAt) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.nextAttemptAt = nextAttemptAt;
    }

    public void refreshPayload(Map<String, Object> payload, OffsetDateTime expiresAt) {
        this.payloadJson = payload;
        this.expiresAt = expiresAt;
    }

    public void markDelivered(OffsetDateTime now) {
        this.attemptCount += 1;
        this.status = ArchDoxAgentCommandStatus.DELIVERED;
        this.deliveredAt = now;
        this.lastAttemptAt = now;
        this.nextAttemptAt = null;
    }

    public void ack(OffsetDateTime now) {
        this.status = ArchDoxAgentCommandStatus.ACKED;
        this.ackAt = now;
    }

    public void complete(Map<String, Object> result, OffsetDateTime now) {
        this.status = ArchDoxAgentCommandStatus.COMPLETED;
        this.resultJson = result;
        this.completedAt = now;
    }

    public void fail(String errorMessage, Map<String, Object> result, OffsetDateTime now) {
        this.status = ArchDoxAgentCommandStatus.FAILED;
        this.errorMessage = errorMessage;
        this.resultJson = result;
        this.failedAt = now;
        this.nextAttemptAt = null;
    }

    public void expire(String errorMessage, OffsetDateTime now) {
        if (isTerminal()) {
            return;
        }
        this.status = ArchDoxAgentCommandStatus.EXPIRED;
        this.errorMessage = errorMessage;
        this.failedAt = now;
        this.nextAttemptAt = null;
    }

    public boolean isTerminal() {
        return status == ArchDoxAgentCommandStatus.COMPLETED
                || status == ArchDoxAgentCommandStatus.FAILED
                || status == ArchDoxAgentCommandStatus.EXPIRED;
    }

    public boolean isDue(OffsetDateTime now) {
        return nextAttemptAt == null || !nextAttemptAt.isAfter(now);
    }

    public Long id() {
        return id;
    }

    public ArchDoxAgent agent() {
        return agent;
    }

    public ArchDoxAgentCommandType commandType() {
        return commandType;
    }

    public Map<String, Object> payloadJson() {
        return payloadJson;
    }

    public ArchDoxAgentCommandStatus status() {
        return status;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public OffsetDateTime nextAttemptAt() {
        return nextAttemptAt;
    }

    public OffsetDateTime expiresAt() {
        return expiresAt;
    }

    public OffsetDateTime deliveredAt() {
        return deliveredAt;
    }

    public OffsetDateTime ackAt() {
        return ackAt;
    }

    public OffsetDateTime completedAt() {
        return completedAt;
    }

    public OffsetDateTime failedAt() {
        return failedAt;
    }

    public Map<String, Object> resultJson() {
        return resultJson;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public OffsetDateTime lastAttemptAt() {
        return lastAttemptAt;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }
}
