package com.archdox.cloud.legal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "legal_sync_runs")
public class LegalSyncRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trigger_type", nullable = false)
    private String triggerType;

    @Column(name = "source_code", nullable = false)
    private String sourceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LegalSyncRunStatus status;

    @Column(name = "started_by_user_id")
    private Long startedByUserId;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "failure_code")
    private String failureCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> summaryJson;

    protected LegalSyncRun() {
    }

    public LegalSyncRun(String triggerType, String sourceCode, Long startedByUserId, OffsetDateTime now) {
        this.triggerType = required(triggerType, "triggerType");
        this.sourceCode = required(sourceCode, "sourceCode");
        this.startedByUserId = startedByUserId;
        this.status = LegalSyncRunStatus.RUNNING;
        this.startedAt = now;
        this.summaryJson = Map.of();
    }

    public void complete(Map<String, Object> summaryJson, OffsetDateTime now) {
        this.status = LegalSyncRunStatus.COMPLETED;
        this.completedAt = now;
        this.failureCode = null;
        this.summaryJson = summaryJson == null ? Map.of() : Map.copyOf(summaryJson);
    }

    public void fail(String failureCode, OffsetDateTime now) {
        fail(failureCode, null, now);
    }

    public void fail(String failureCode, String failureMessage, OffsetDateTime now) {
        this.status = LegalSyncRunStatus.FAILED;
        this.failureCode = blankToNull(failureCode);
        this.completedAt = now;
        this.summaryJson = failureMessage == null || failureMessage.isBlank()
                ? Map.of("failureCode", this.failureCode == null ? "UNKNOWN" : this.failureCode)
                : Map.of(
                        "failureCode", this.failureCode == null ? "UNKNOWN" : this.failureCode,
                        "failureMessage", failureMessage.trim());
    }

    public Long id() {
        return id;
    }

    public String triggerType() {
        return triggerType;
    }

    public String sourceCode() {
        return sourceCode;
    }

    public LegalSyncRunStatus status() {
        return status;
    }

    public OffsetDateTime startedAt() {
        return startedAt;
    }

    public OffsetDateTime completedAt() {
        return completedAt;
    }

    public String failureCode() {
        return failureCode;
    }

    public Map<String, Object> summaryJson() {
        return summaryJson;
    }

    private static String required(String value, String fieldName) {
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
