package com.archdox.cloud.configuration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "office_config_overrides")
public class OfficeConfigOverride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "report_type", nullable = false)
    private String reportType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfficeConfigOverrideStatus status = OfficeConfigOverrideStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_revision_id")
    private DocumentTemplateRevision templateRevision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_revision_id")
    private WorkflowDefinitionRevision workflowRevision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_set_revision_id")
    private RuleSetRevision ruleSetRevision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "output_layout_revision_id")
    private OutputLayoutConfigRevision outputLayoutRevision;

    @Column(name = "effective_from")
    private OffsetDateTime effectiveFrom;

    @Column(name = "effective_to")
    private OffsetDateTime effectiveTo;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected OfficeConfigOverride() {
    }

    public OfficeConfigOverride(Long officeId, String reportType, Long createdBy, OffsetDateTime now) {
        this.officeId = officeId;
        this.reportType = reportType;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            DocumentTemplateRevision templateRevision,
            WorkflowDefinitionRevision workflowRevision,
            RuleSetRevision ruleSetRevision,
            OutputLayoutConfigRevision outputLayoutRevision,
            OffsetDateTime effectiveFrom,
            OffsetDateTime effectiveTo,
            Long updatedBy,
            OffsetDateTime now
    ) {
        this.status = OfficeConfigOverrideStatus.ACTIVE;
        this.templateRevision = templateRevision;
        this.workflowRevision = workflowRevision;
        this.ruleSetRevision = ruleSetRevision;
        this.outputLayoutRevision = outputLayoutRevision;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public String reportType() {
        return reportType;
    }

    public OfficeConfigOverrideStatus status() {
        return status;
    }

    public DocumentTemplateRevision templateRevision() {
        return templateRevision;
    }

    public WorkflowDefinitionRevision workflowRevision() {
        return workflowRevision;
    }

    public RuleSetRevision ruleSetRevision() {
        return ruleSetRevision;
    }

    public OutputLayoutConfigRevision outputLayoutRevision() {
        return outputLayoutRevision;
    }

    public OffsetDateTime effectiveFrom() {
        return effectiveFrom;
    }

    public OffsetDateTime effectiveTo() {
        return effectiveTo;
    }

    public Long createdBy() {
        return createdBy;
    }

    public Long updatedBy() {
        return updatedBy;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }
}
