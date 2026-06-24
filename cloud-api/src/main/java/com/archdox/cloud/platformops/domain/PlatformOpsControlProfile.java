package com.archdox.cloud.platformops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "platform_ops_control_profiles")
public class PlatformOpsControlProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_kind", nullable = false)
    private PlatformOpsControlSignalKind signalKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false)
    private PlatformOpsControlProfileScope scopeType;

    @Column(name = "model_id")
    private String modelId;

    @Column(name = "signal_key", nullable = false, length = 96)
    private String signalKey;

    @Column(name = "signal_text", nullable = false)
    private String signalText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlatformOpsFindingSeverity severity;

    @Column(name = "i_weight", nullable = false)
    private BigDecimal iWeight;

    @Column(name = "hit_count", nullable = false)
    private int hitCount;

    @Column(name = "source_daily_report_id")
    private Long sourceDailyReportId;

    @Column
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlatformOpsControlProfileStatus status;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "first_observed_at", nullable = false)
    private OffsetDateTime firstObservedAt;

    @Column(name = "last_observed_at", nullable = false)
    private OffsetDateTime lastObservedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PlatformOpsControlProfile() {
    }

    public PlatformOpsControlProfile(
            PlatformOpsControlSignalKind signalKind,
            PlatformOpsControlProfileScope scopeType,
            String modelId,
            String signalKey,
            String signalText,
            PlatformOpsFindingSeverity severity,
            BigDecimal iWeight,
            Long sourceDailyReportId,
            String notes,
            Long userId,
            OffsetDateTime now
    ) {
        this.signalKind = signalKind;
        this.scopeType = scopeType;
        this.modelId = blankToNull(modelId);
        this.signalKey = signalKey;
        this.signalText = signalText.trim();
        this.severity = severity == null ? PlatformOpsFindingSeverity.WARN : severity;
        this.iWeight = iWeight == null ? BigDecimal.ONE : iWeight;
        this.hitCount = 1;
        this.sourceDailyReportId = sourceDailyReportId;
        this.notes = blankToNull(notes);
        this.status = PlatformOpsControlProfileStatus.ACTIVE;
        this.createdByUserId = userId;
        this.updatedByUserId = userId;
        this.firstObservedAt = now;
        this.lastObservedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void observe(
            PlatformOpsFindingSeverity severity,
            BigDecimal iWeight,
            Long sourceDailyReportId,
            String notes,
            Long userId,
            OffsetDateTime now
    ) {
        this.severity = severity == null ? this.severity : severity;
        this.iWeight = iWeight == null ? this.iWeight : iWeight;
        this.sourceDailyReportId = sourceDailyReportId == null ? this.sourceDailyReportId : sourceDailyReportId;
        this.notes = blankToNull(notes) == null ? this.notes : notes.trim();
        this.status = PlatformOpsControlProfileStatus.ACTIVE;
        this.hitCount += 1;
        this.lastObservedAt = now;
        this.updatedByUserId = userId;
        this.updatedAt = now;
    }

    public void update(
            PlatformOpsControlProfileStatus status,
            PlatformOpsFindingSeverity severity,
            BigDecimal iWeight,
            String notes,
            Long userId,
            OffsetDateTime now
    ) {
        if (status != null) {
            this.status = status;
        }
        if (severity != null) {
            this.severity = severity;
        }
        if (iWeight != null) {
            this.iWeight = iWeight;
        }
        this.notes = blankToNull(notes);
        this.updatedByUserId = userId;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public PlatformOpsControlSignalKind signalKind() {
        return signalKind;
    }

    public PlatformOpsControlProfileScope scopeType() {
        return scopeType;
    }

    public String modelId() {
        return modelId;
    }

    public String signalKey() {
        return signalKey;
    }

    public String signalText() {
        return signalText;
    }

    public PlatformOpsFindingSeverity severity() {
        return severity;
    }

    public BigDecimal iWeight() {
        return iWeight;
    }

    public int hitCount() {
        return hitCount;
    }

    public Long sourceDailyReportId() {
        return sourceDailyReportId;
    }

    public String notes() {
        return notes;
    }

    public PlatformOpsControlProfileStatus status() {
        return status;
    }

    public Long createdByUserId() {
        return createdByUserId;
    }

    public Long updatedByUserId() {
        return updatedByUserId;
    }

    public OffsetDateTime firstObservedAt() {
        return firstObservedAt;
    }

    public OffsetDateTime lastObservedAt() {
        return lastObservedAt;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
