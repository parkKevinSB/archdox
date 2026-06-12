package com.archdox.cloud.inspection.domain;

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
@Table(name = "inspection_reports")
public class InspectionReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "report_no", nullable = false)
    private String reportNo;

    @Column(name = "report_type", nullable = false)
    private String reportType;

    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InspectionReportStatus status = InspectionReportStatus.DRAFT;

    @Column(name = "current_step")
    private String currentStep;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "archdox_agent_id")
    private Long archDoxAgentId;

    @Column(name = "content_revision", nullable = false)
    private int contentRevision = 1;

    @Column(name = "submitted_revision")
    private Integer submittedRevision;

    @Column(name = "generated_revision")
    private Integer generatedRevision;

    @Column(name = "last_document_job_id")
    private Long lastDocumentJobId;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected InspectionReport() {
    }

    public InspectionReport(
            Long officeId,
            Long projectId,
            Long siteId,
            String reportNo,
            String reportType,
            String title,
            Long templateId,
            Long requestedBy,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.projectId = projectId;
        this.siteId = siteId;
        this.reportNo = reportNo;
        this.reportType = reportType;
        this.title = title;
        this.templateId = templateId;
        this.requestedBy = requestedBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markStepSaved(String stepCode, OffsetDateTime now) {
        this.currentStep = stepCode;
        if (status == InspectionReportStatus.DRAFT) {
            this.status = InspectionReportStatus.STEP_SAVED;
        }
        this.updatedAt = now;
    }

    public boolean canSaveStep() {
        return status == InspectionReportStatus.DRAFT || status == InspectionReportStatus.STEP_SAVED;
    }

    public boolean canApplyPreflightFixToSubmittedRevision() {
        return status == InspectionReportStatus.READY_TO_GENERATE
                || status == InspectionReportStatus.GENERATED
                || status == InspectionReportStatus.FAILED;
    }

    public boolean canSubmit() {
        return status == InspectionReportStatus.DRAFT || status == InspectionReportStatus.STEP_SAVED;
    }

    public boolean canReopenForEdit() {
        return status == InspectionReportStatus.READY_TO_GENERATE
                || status == InspectionReportStatus.GENERATED
                || status == InspectionReportStatus.DELIVERED
                || status == InspectionReportStatus.FAILED;
    }

    public boolean canCancel() {
        return status == InspectionReportStatus.DRAFT
                || status == InspectionReportStatus.STEP_SAVED
                || status == InspectionReportStatus.READY_TO_GENERATE;
    }

    public void submit(OffsetDateTime now) {
        this.status = InspectionReportStatus.READY_TO_GENERATE;
        this.submittedRevision = contentRevision;
        this.updatedAt = now;
    }

    public void markSubmittedPreflightFixApplied(String stepCode, OffsetDateTime now) {
        this.currentStep = stepCode;
        if (status == InspectionReportStatus.GENERATED || status == InspectionReportStatus.FAILED) {
            this.status = InspectionReportStatus.READY_TO_GENERATE;
        }
        this.updatedAt = now;
    }

    public boolean canRequestGeneration() {
        return status == InspectionReportStatus.READY_TO_GENERATE
                || status == InspectionReportStatus.GENERATED
                || status == InspectionReportStatus.FAILED;
    }

    public int generationRevision() {
        return submittedRevision == null ? contentRevision : submittedRevision;
    }

    public void requestGeneration(Long documentJobId, int reportRevision, OffsetDateTime now) {
        this.status = InspectionReportStatus.GENERATION_REQUESTED;
        this.lastDocumentJobId = documentJobId;
        if (this.submittedRevision == null) {
            this.submittedRevision = reportRevision;
        }
        this.updatedAt = now;
    }

    public void markGenerating(OffsetDateTime now) {
        this.status = InspectionReportStatus.GENERATING;
        this.updatedAt = now;
    }

    public void markGenerated(int reportRevision, OffsetDateTime now) {
        this.status = InspectionReportStatus.GENERATED;
        this.generatedRevision = reportRevision;
        this.generatedAt = now;
        this.updatedAt = now;
    }

    public void markGenerationFailed(OffsetDateTime now) {
        this.status = InspectionReportStatus.FAILED;
        this.updatedAt = now;
    }

    public void cancel(OffsetDateTime now) {
        this.status = InspectionReportStatus.CANCELLED;
        this.updatedAt = now;
    }

    public void reopenForEdit(OffsetDateTime now) {
        this.contentRevision += 1;
        this.status = InspectionReportStatus.STEP_SAVED;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public Long projectId() {
        return projectId;
    }

    public Long siteId() {
        return siteId;
    }

    public String reportNo() {
        return reportNo;
    }

    public String reportType() {
        return reportType;
    }

    public String title() {
        return title;
    }

    public InspectionReportStatus status() {
        return status;
    }

    public String currentStep() {
        return currentStep;
    }

    public Long templateId() {
        return templateId;
    }

    public Long archDoxAgentId() {
        return archDoxAgentId;
    }

    public int contentRevision() {
        return contentRevision;
    }

    public Integer submittedRevision() {
        return submittedRevision;
    }

    public Integer generatedRevision() {
        return generatedRevision;
    }

    public Long lastDocumentJobId() {
        return lastDocumentJobId;
    }

    public Long requestedBy() {
        return requestedBy;
    }
}
