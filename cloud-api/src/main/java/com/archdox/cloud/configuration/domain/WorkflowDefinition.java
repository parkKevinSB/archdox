package com.archdox.cloud.configuration.domain;

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
@Table(name = "workflow_definitions")
public class WorkflowDefinition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id")
    private Long officeId;

    @Column(name = "workflow_code", nullable = false)
    private String workflowCode;

    @Column(nullable = false)
    private String name;

    @Column(name = "report_type")
    private String reportType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConfigDefinitionStatus status = ConfigDefinitionStatus.ACTIVE;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected WorkflowDefinition() {
    }

    public WorkflowDefinition(Long officeId, String workflowCode, String name, String reportType, Long createdBy, OffsetDateTime now) {
        this.officeId = officeId;
        this.workflowCode = workflowCode;
        this.name = name;
        this.reportType = reportType;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public String workflowCode() {
        return workflowCode;
    }

    public String name() {
        return name;
    }

    public String reportType() {
        return reportType;
    }

    public ConfigDefinitionStatus status() {
        return status;
    }

    public Long createdBy() {
        return createdBy;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }
}
