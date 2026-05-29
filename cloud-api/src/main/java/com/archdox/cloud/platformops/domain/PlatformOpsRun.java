package com.archdox.cloud.platformops.domain;

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
@Table(name = "platform_ops_runs")
public class PlatformOpsRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private PlatformOpsRunTriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlatformOpsRunStatus status;

    @Column(name = "started_by_user_id")
    private Long startedByUserId;

    @Column(name = "incident_id")
    private Long incidentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> inputSnapshotJson;

    @Column(name = "ai_harness_run_id")
    private String aiHarnessRunId;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "failure_code")
    private String failureCode;

    protected PlatformOpsRun() {
    }

    public PlatformOpsRun(PlatformOpsRunTriggerType triggerType, Long startedByUserId, Map<String, Object> inputSnapshotJson, OffsetDateTime now) {
        this.triggerType = triggerType;
        this.status = PlatformOpsRunStatus.RUNNING;
        this.startedByUserId = startedByUserId;
        this.inputSnapshotJson = inputSnapshotJson == null ? Map.of() : Map.copyOf(inputSnapshotJson);
        this.startedAt = now;
    }

    public void attachIncident(Long incidentId) {
        if (this.incidentId == null) {
            this.incidentId = incidentId;
        }
    }

    public void replaceSnapshot(Map<String, Object> inputSnapshotJson) {
        this.inputSnapshotJson = inputSnapshotJson == null ? Map.of() : Map.copyOf(inputSnapshotJson);
    }

    public void attachAiHarnessRun(String aiHarnessRunId) {
        this.aiHarnessRunId = blankToNull(aiHarnessRunId);
    }

    public void complete(OffsetDateTime now) {
        this.status = PlatformOpsRunStatus.COMPLETED;
        this.completedAt = now;
        this.failureCode = null;
    }

    public void fail(String failureCode, OffsetDateTime now) {
        this.status = PlatformOpsRunStatus.FAILED;
        this.failureCode = blankToNull(failureCode);
        this.completedAt = now;
    }

    public Long id() {
        return id;
    }

    public PlatformOpsRunTriggerType triggerType() {
        return triggerType;
    }

    public PlatformOpsRunStatus status() {
        return status;
    }

    public Long startedByUserId() {
        return startedByUserId;
    }

    public Long incidentId() {
        return incidentId;
    }

    public Map<String, Object> inputSnapshotJson() {
        return inputSnapshotJson;
    }

    public String aiHarnessRunId() {
        return aiHarnessRunId;
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
