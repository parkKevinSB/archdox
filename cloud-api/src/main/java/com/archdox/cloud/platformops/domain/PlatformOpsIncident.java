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

@Entity
@Table(name = "platform_ops_incidents")
public class PlatformOpsIncident {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlatformOpsIncidentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlatformOpsFindingSeverity severity;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String summary;

    @Column(name = "office_id")
    private Long officeId;

    @Column(name = "primary_resource_type")
    private String primaryResourceType;

    @Column(name = "primary_resource_id")
    private String primaryResourceId;

    @Column(name = "first_seen_at", nullable = false)
    private OffsetDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "created_by_run_id")
    private Long createdByRunId;

    protected PlatformOpsIncident() {
    }

    public PlatformOpsIncident(
            PlatformOpsFindingSeverity severity,
            String category,
            String title,
            String summary,
            Long officeId,
            String primaryResourceType,
            String primaryResourceId,
            Long createdByRunId,
            OffsetDateTime now
    ) {
        this.status = PlatformOpsIncidentStatus.OPEN;
        this.severity = severity;
        this.category = required(category);
        this.title = required(title);
        this.summary = required(summary);
        this.officeId = officeId;
        this.primaryResourceType = blankToNull(primaryResourceType);
        this.primaryResourceId = blankToNull(primaryResourceId);
        this.createdByRunId = createdByRunId;
        this.firstSeenAt = now;
        this.lastSeenAt = now;
    }

    public void observe(PlatformOpsFindingSeverity severity, String title, String summary, Long runId, OffsetDateTime now) {
        if (moreSevere(severity, this.severity)) {
            this.severity = severity;
        }
        this.title = required(title);
        this.summary = required(summary);
        this.lastSeenAt = now;
        if (this.createdByRunId == null) {
            this.createdByRunId = runId;
        }
        if (this.status == PlatformOpsIncidentStatus.RESOLVED || this.status == PlatformOpsIncidentStatus.IGNORED) {
            this.status = PlatformOpsIncidentStatus.OPEN;
            this.resolvedAt = null;
        }
    }

    public void resolve(String summary, OffsetDateTime now) {
        if (this.status == PlatformOpsIncidentStatus.IGNORED) {
            return;
        }
        this.status = PlatformOpsIncidentStatus.RESOLVED;
        this.summary = required(summary);
        this.resolvedAt = now;
        this.lastSeenAt = now;
    }

    public Long id() {
        return id;
    }

    public PlatformOpsIncidentStatus status() {
        return status;
    }

    public PlatformOpsFindingSeverity severity() {
        return severity;
    }

    public String category() {
        return category;
    }

    public String title() {
        return title;
    }

    public String summary() {
        return summary;
    }

    public Long officeId() {
        return officeId;
    }

    public String primaryResourceType() {
        return primaryResourceType;
    }

    public String primaryResourceId() {
        return primaryResourceId;
    }

    public OffsetDateTime firstSeenAt() {
        return firstSeenAt;
    }

    public OffsetDateTime lastSeenAt() {
        return lastSeenAt;
    }

    public OffsetDateTime resolvedAt() {
        return resolvedAt;
    }

    public Long createdByRunId() {
        return createdByRunId;
    }

    private boolean moreSevere(PlatformOpsFindingSeverity next, PlatformOpsFindingSeverity current) {
        if (next == null) {
            return false;
        }
        return current == null || next.ordinal() > current.ordinal();
    }

    private String required(String value) {
        var normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Platform ops incident value is required");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
