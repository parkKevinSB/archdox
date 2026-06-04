package com.archdox.cloud.engine.auth.domain;

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
@Table(name = "engine_api_keys")
public class EngineApiKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_id", nullable = false, unique = true)
    private String keyId;

    @Column(name = "key_prefix", nullable = false)
    private String keyPrefix;

    @Column(name = "secret_hash", nullable = false)
    private String secretHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "office_id")
    private Long officeId;

    @Column(name = "issued_by_user_id", nullable = false)
    private Long issuedByUserId;

    @Column(nullable = false)
    private String scopes;

    @Column(name = "daily_request_unit_limit", nullable = false)
    private Integer dailyRequestUnitLimit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EngineApiKeyStatus status;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected EngineApiKey() {
    }

    public EngineApiKey(
            String keyId,
            String keyPrefix,
            String secretHash,
            String displayName,
            Long ownerUserId,
            Long officeId,
            Long issuedByUserId,
            String scopes,
            Integer dailyRequestUnitLimit,
            OffsetDateTime expiresAt,
            OffsetDateTime now
    ) {
        this.keyId = require(keyId, "keyId");
        this.keyPrefix = require(keyPrefix, "keyPrefix");
        this.secretHash = require(secretHash, "secretHash");
        this.displayName = require(displayName, "displayName");
        this.ownerUserId = ownerUserId;
        this.officeId = officeId;
        this.issuedByUserId = issuedByUserId;
        this.scopes = require(scopes, "scopes");
        this.dailyRequestUnitLimit = dailyRequestUnitLimit;
        this.status = EngineApiKeyStatus.ACTIVE;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void touchLastUsed(OffsetDateTime now) {
        this.lastUsedAt = now;
        this.updatedAt = now;
    }

    public void revoke(OffsetDateTime now) {
        if (this.status == EngineApiKeyStatus.REVOKED) {
            return;
        }
        this.status = EngineApiKeyStatus.REVOKED;
        this.revokedAt = now;
        this.updatedAt = now;
    }

    public boolean expired(OffsetDateTime now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public Long id() {
        return id;
    }

    public String keyId() {
        return keyId;
    }

    public String keyPrefix() {
        return keyPrefix;
    }

    public String secretHash() {
        return secretHash;
    }

    public String displayName() {
        return displayName;
    }

    public Long ownerUserId() {
        return ownerUserId;
    }

    public Long officeId() {
        return officeId;
    }

    public Long issuedByUserId() {
        return issuedByUserId;
    }

    public String scopes() {
        return scopes;
    }

    public Integer dailyRequestUnitLimit() {
        return dailyRequestUnitLimit;
    }

    public EngineApiKeyStatus status() {
        return status;
    }

    public OffsetDateTime expiresAt() {
        return expiresAt;
    }

    public OffsetDateTime lastUsedAt() {
        return lastUsedAt;
    }

    public OffsetDateTime revokedAt() {
        return revokedAt;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    private static String require(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
