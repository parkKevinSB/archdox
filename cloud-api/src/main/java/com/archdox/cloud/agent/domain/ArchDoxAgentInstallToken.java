package com.archdox.cloud.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "archdox_agent_install_tokens")
public class ArchDoxAgentInstallToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "agent_id")
    private Long agentId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArchDoxAgentInstallTokenStatus status;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ArchDoxAgentInstallToken() {
    }

    public ArchDoxAgentInstallToken(
            Long officeId,
            Long agentId,
            String tokenHash,
            OffsetDateTime expiresAt,
            Long createdBy,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.agentId = agentId;
        this.tokenHash = tokenHash;
        this.status = ArchDoxAgentInstallTokenStatus.ACTIVE;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public ArchDoxAgentInstallToken(
            Long officeId,
            String tokenHash,
            OffsetDateTime expiresAt,
            Long createdBy,
            OffsetDateTime now
    ) {
        this(officeId, null, tokenHash, expiresAt, createdBy, now);
    }

    public boolean canUse(OffsetDateTime now) {
        return status == ArchDoxAgentInstallTokenStatus.ACTIVE && expiresAt.isAfter(now);
    }

    public void markUsed(OffsetDateTime now) {
        this.status = ArchDoxAgentInstallTokenStatus.USED;
        this.usedAt = now;
        this.updatedAt = now;
    }

    public void markExpired(OffsetDateTime now) {
        this.status = ArchDoxAgentInstallTokenStatus.EXPIRED;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public Long agentId() {
        return agentId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public ArchDoxAgentInstallTokenStatus status() {
        return status;
    }

    public OffsetDateTime expiresAt() {
        return expiresAt;
    }
}
