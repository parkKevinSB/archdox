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
@Table(name = "platform_ops_findings")
public class PlatformOpsFinding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id")
    private Long incidentId;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "office_id")
    private Long officeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlatformOpsFindingSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlatformOpsFindingSource source;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String message;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "workflow_type")
    private String workflowType;

    @Column(name = "workflow_key")
    private String workflowKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> evidenceJson;

    @Column
    private String recommendation;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected PlatformOpsFinding() {
    }

    public PlatformOpsFinding(
            Long incidentId,
            Long runId,
            Long officeId,
            PlatformOpsFindingSeverity severity,
            PlatformOpsFindingSource source,
            String code,
            String category,
            String title,
            String message,
            String resourceType,
            String resourceId,
            String workflowType,
            String workflowKey,
            Map<String, Object> evidenceJson,
            String recommendation,
            OffsetDateTime createdAt
    ) {
        this.incidentId = incidentId;
        this.runId = runId;
        this.officeId = officeId;
        this.severity = severity == null ? PlatformOpsFindingSeverity.INFO : severity;
        this.source = source == null ? PlatformOpsFindingSource.DETECTOR : source;
        this.code = required(code);
        this.category = required(category);
        this.title = required(title);
        this.message = required(message);
        this.resourceType = blankToNull(resourceType);
        this.resourceId = blankToNull(resourceId);
        this.workflowType = blankToNull(workflowType);
        this.workflowKey = blankToNull(workflowKey);
        this.evidenceJson = evidenceJson == null ? Map.of() : Map.copyOf(evidenceJson);
        this.recommendation = blankToNull(recommendation);
        this.createdAt = createdAt;
    }

    public Long id() {
        return id;
    }

    public Long incidentId() {
        return incidentId;
    }

    public Long runId() {
        return runId;
    }

    public Long officeId() {
        return officeId;
    }

    public PlatformOpsFindingSeverity severity() {
        return severity;
    }

    public PlatformOpsFindingSource source() {
        return source;
    }

    public String code() {
        return code;
    }

    public String category() {
        return category;
    }

    public String title() {
        return title;
    }

    public String message() {
        return message;
    }

    public String resourceType() {
        return resourceType;
    }

    public String resourceId() {
        return resourceId;
    }

    public String workflowType() {
        return workflowType;
    }

    public String workflowKey() {
        return workflowKey;
    }

    public Map<String, Object> evidenceJson() {
        return evidenceJson;
    }

    public String recommendation() {
        return recommendation;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    private String required(String value) {
        var normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Platform ops finding value is required");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
