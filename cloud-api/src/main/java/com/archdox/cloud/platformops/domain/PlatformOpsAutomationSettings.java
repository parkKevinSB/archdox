package com.archdox.cloud.platformops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "platform_ops_automation_settings")
public class PlatformOpsAutomationSettings {
    public static final String SINGLETON_KEY = "default";

    @Id
    @Column(name = "singleton_key", nullable = false, length = 64)
    private String singletonKey;

    @Column(name = "detection_enabled", nullable = false)
    private boolean detectionEnabled;

    @Column(name = "detection_check_interval_ms", nullable = false)
    private long detectionCheckIntervalMs;

    @Column(name = "document_job_stuck_minutes", nullable = false)
    private long documentJobStuckMinutes;

    @Column(name = "agent_command_stuck_minutes", nullable = false)
    private long agentCommandStuckMinutes;

    @Column(name = "photo_pickup_stuck_minutes", nullable = false)
    private long photoPickupStuckMinutes;

    @Column(name = "delivery_stuck_minutes", nullable = false)
    private long deliveryStuckMinutes;

    @Column(name = "max_detected_items", nullable = false)
    private int maxDetectedItems;

    @Column(name = "daily_report_enabled", nullable = false)
    private boolean dailyReportEnabled;

    @Column(name = "daily_report_run_time", nullable = false, length = 16)
    private String dailyReportRunTime;

    @Column(name = "daily_report_zone_id", nullable = false, length = 80)
    private String dailyReportZoneId;

    @Column(name = "daily_report_check_interval_ms", nullable = false)
    private long dailyReportCheckIntervalMs;

    @Column(name = "daily_report_catch_up_grace_minutes", nullable = false)
    private long dailyReportCatchUpGraceMinutes;

    @Column(name = "daily_report_auto_diagnosis_enabled", nullable = false)
    private boolean dailyReportAutoDiagnosisEnabled;

    @Column(name = "daily_report_auto_diagnosis_incident_limit", nullable = false)
    private int dailyReportAutoDiagnosisIncidentLimit;

    @Column(name = "daily_report_auto_diagnosis_min_severity", nullable = false, length = 40)
    private String dailyReportAutoDiagnosisMinSeverity;

    @Column(name = "daily_report_directory", nullable = false)
    private String dailyReportDirectory;

    @Column(name = "retention_enabled", nullable = false)
    private boolean retentionEnabled;

    @Column(name = "retention_days", nullable = false)
    private int retentionDays;

    @Column(name = "retention_check_interval_ms", nullable = false)
    private long retentionCheckIntervalMs;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PlatformOpsAutomationSettings() {
    }

    public PlatformOpsAutomationSettings(
            boolean detectionEnabled,
            long detectionCheckIntervalMs,
            long documentJobStuckMinutes,
            long agentCommandStuckMinutes,
            long photoPickupStuckMinutes,
            long deliveryStuckMinutes,
            int maxDetectedItems,
            boolean dailyReportEnabled,
            String dailyReportRunTime,
            String dailyReportZoneId,
            long dailyReportCheckIntervalMs,
            long dailyReportCatchUpGraceMinutes,
            boolean dailyReportAutoDiagnosisEnabled,
            int dailyReportAutoDiagnosisIncidentLimit,
            String dailyReportAutoDiagnosisMinSeverity,
            String dailyReportDirectory,
            boolean retentionEnabled,
            int retentionDays,
            long retentionCheckIntervalMs,
            Long updatedByUserId,
            OffsetDateTime now
    ) {
        this.singletonKey = SINGLETON_KEY;
        update(
                detectionEnabled,
                detectionCheckIntervalMs,
                documentJobStuckMinutes,
                agentCommandStuckMinutes,
                photoPickupStuckMinutes,
                deliveryStuckMinutes,
                maxDetectedItems,
                dailyReportEnabled,
                dailyReportRunTime,
                dailyReportZoneId,
                dailyReportCheckIntervalMs,
                dailyReportCatchUpGraceMinutes,
                dailyReportAutoDiagnosisEnabled,
                dailyReportAutoDiagnosisIncidentLimit,
                dailyReportAutoDiagnosisMinSeverity,
                dailyReportDirectory,
                retentionEnabled,
                retentionDays,
                retentionCheckIntervalMs,
                updatedByUserId,
                now);
        this.createdAt = now;
    }

    public void update(
            boolean detectionEnabled,
            long detectionCheckIntervalMs,
            long documentJobStuckMinutes,
            long agentCommandStuckMinutes,
            long photoPickupStuckMinutes,
            long deliveryStuckMinutes,
            int maxDetectedItems,
            boolean dailyReportEnabled,
            String dailyReportRunTime,
            String dailyReportZoneId,
            long dailyReportCheckIntervalMs,
            long dailyReportCatchUpGraceMinutes,
            boolean dailyReportAutoDiagnosisEnabled,
            int dailyReportAutoDiagnosisIncidentLimit,
            String dailyReportAutoDiagnosisMinSeverity,
            String dailyReportDirectory,
            boolean retentionEnabled,
            int retentionDays,
            long retentionCheckIntervalMs,
            Long updatedByUserId,
            OffsetDateTime now
    ) {
        this.detectionEnabled = detectionEnabled;
        this.detectionCheckIntervalMs = detectionCheckIntervalMs;
        this.documentJobStuckMinutes = documentJobStuckMinutes;
        this.agentCommandStuckMinutes = agentCommandStuckMinutes;
        this.photoPickupStuckMinutes = photoPickupStuckMinutes;
        this.deliveryStuckMinutes = deliveryStuckMinutes;
        this.maxDetectedItems = maxDetectedItems;
        this.dailyReportEnabled = dailyReportEnabled;
        this.dailyReportRunTime = dailyReportRunTime;
        this.dailyReportZoneId = dailyReportZoneId;
        this.dailyReportCheckIntervalMs = dailyReportCheckIntervalMs;
        this.dailyReportCatchUpGraceMinutes = dailyReportCatchUpGraceMinutes;
        this.dailyReportAutoDiagnosisEnabled = dailyReportAutoDiagnosisEnabled;
        this.dailyReportAutoDiagnosisIncidentLimit = dailyReportAutoDiagnosisIncidentLimit;
        this.dailyReportAutoDiagnosisMinSeverity = dailyReportAutoDiagnosisMinSeverity;
        this.dailyReportDirectory = dailyReportDirectory;
        this.retentionEnabled = retentionEnabled;
        this.retentionDays = retentionDays;
        this.retentionCheckIntervalMs = retentionCheckIntervalMs;
        this.updatedByUserId = updatedByUserId;
        this.updatedAt = now;
    }

    public boolean detectionEnabled() {
        return detectionEnabled;
    }

    public long detectionCheckIntervalMs() {
        return detectionCheckIntervalMs;
    }

    public long documentJobStuckMinutes() {
        return documentJobStuckMinutes;
    }

    public long agentCommandStuckMinutes() {
        return agentCommandStuckMinutes;
    }

    public long photoPickupStuckMinutes() {
        return photoPickupStuckMinutes;
    }

    public long deliveryStuckMinutes() {
        return deliveryStuckMinutes;
    }

    public int maxDetectedItems() {
        return maxDetectedItems;
    }

    public boolean dailyReportEnabled() {
        return dailyReportEnabled;
    }

    public String dailyReportRunTime() {
        return dailyReportRunTime;
    }

    public String dailyReportZoneId() {
        return dailyReportZoneId;
    }

    public long dailyReportCheckIntervalMs() {
        return dailyReportCheckIntervalMs;
    }

    public long dailyReportCatchUpGraceMinutes() {
        return dailyReportCatchUpGraceMinutes;
    }

    public boolean dailyReportAutoDiagnosisEnabled() {
        return dailyReportAutoDiagnosisEnabled;
    }

    public int dailyReportAutoDiagnosisIncidentLimit() {
        return dailyReportAutoDiagnosisIncidentLimit;
    }

    public String dailyReportAutoDiagnosisMinSeverity() {
        return dailyReportAutoDiagnosisMinSeverity;
    }

    public String dailyReportDirectory() {
        return dailyReportDirectory;
    }

    public boolean retentionEnabled() {
        return retentionEnabled;
    }

    public int retentionDays() {
        return retentionDays;
    }

    public long retentionCheckIntervalMs() {
        return retentionCheckIntervalMs;
    }

    public Long updatedByUserId() {
        return updatedByUserId;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }
}
