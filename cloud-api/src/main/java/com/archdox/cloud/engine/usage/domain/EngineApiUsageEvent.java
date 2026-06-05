package com.archdox.cloud.engine.usage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "engine_api_usage_events")
public class EngineApiUsageEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    @Column(name = "key_id", nullable = false)
    private String keyId;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "office_id")
    private Long officeId;

    @Column(nullable = false)
    private String capability;

    @Column(nullable = false)
    private String operation;

    @Column(name = "review_session_id")
    private String reviewSessionId;

    @Column(nullable = false)
    private String status;

    @Column(name = "request_units", nullable = false)
    private int requestUnits;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadataJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected EngineApiUsageEvent() {
    }

    public EngineApiUsageEvent(
            Long apiKeyId,
            String keyId,
            Long ownerUserId,
            Long officeId,
            String capability,
            String operation,
            String reviewSessionId,
            String status,
            int requestUnits,
            Map<String, Object> metadataJson,
            OffsetDateTime createdAt
    ) {
        this.apiKeyId = apiKeyId;
        this.keyId = require(keyId, "keyId");
        this.ownerUserId = ownerUserId;
        this.officeId = officeId;
        this.capability = require(capability, "capability");
        this.operation = require(operation, "operation");
        this.reviewSessionId = blankToNull(reviewSessionId);
        this.status = require(status, "status");
        this.requestUnits = Math.max(0, requestUnits);
        this.metadataJson = metadataJson == null ? Map.of() : Map.copyOf(metadataJson);
        this.createdAt = createdAt == null ? OffsetDateTime.now() : createdAt;
    }

    public Long id() {
        return id;
    }

    public Long apiKeyId() {
        return apiKeyId;
    }

    public String keyId() {
        return keyId;
    }

    public Long ownerUserId() {
        return ownerUserId;
    }

    public Long officeId() {
        return officeId;
    }

    public String capability() {
        return capability;
    }

    public String operation() {
        return operation;
    }

    public String reviewSessionId() {
        return reviewSessionId;
    }

    public String status() {
        return status;
    }

    public int requestUnits() {
        return requestUnits;
    }

    public Map<String, Object> metadataJson() {
        return metadataJson;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    private static String require(String value, String fieldName) {
        var normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
