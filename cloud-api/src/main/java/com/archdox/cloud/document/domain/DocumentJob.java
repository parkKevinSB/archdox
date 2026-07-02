package com.archdox.cloud.document.domain;

import com.archdox.document.OutputFormat;
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
@Table(name = "document_jobs")
public class DocumentJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "report_revision", nullable = false)
    private int reportRevision;

    @Column(name = "template_id")
    private Long templateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentJobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "progress_step", nullable = false)
    private DocumentJobProgressStep progressStep;

    @Column(name = "progress_percent", nullable = false)
    private int progressPercent;

    @Column(name = "progress_message")
    private String progressMessage;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "worker_type", nullable = false)
    private DocumentWorkerType workerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_format", nullable = false)
    private OutputFormat outputFormat;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> inputSnapshotJson;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DocumentJob() {
    }

    public DocumentJob(
            Long officeId,
            Long reportId,
            Long projectId,
            int reportRevision,
            Long templateId,
            Long requestedBy,
            DocumentWorkerType workerType,
            OutputFormat outputFormat,
            Map<String, Object> inputSnapshotJson,
            OffsetDateTime now
    ) {
        this(
                officeId,
                reportId,
                projectId,
                reportRevision,
                templateId,
                requestedBy,
                null,
                workerType,
                outputFormat,
                inputSnapshotJson,
                now);
    }

    public DocumentJob(
            Long officeId,
            Long reportId,
            Long projectId,
            int reportRevision,
            Long templateId,
            Long requestedBy,
            String idempotencyKey,
            DocumentWorkerType workerType,
            OutputFormat outputFormat,
            Map<String, Object> inputSnapshotJson,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.reportId = reportId;
        this.projectId = projectId;
        this.reportRevision = reportRevision;
        this.templateId = templateId;
        this.status = DocumentJobStatus.REQUESTED;
        this.progressStep = DocumentJobProgressStep.QUEUED;
        this.progressPercent = 0;
        this.progressMessage = "문서 생성 요청이 접수되었습니다.";
        this.requestedBy = requestedBy;
        this.idempotencyKey = blankToNull(idempotencyKey);
        this.workerType = workerType;
        this.outputFormat = outputFormat;
        this.inputSnapshotJson = inputSnapshotJson;
        this.requestedAt = now;
        this.updatedAt = now;
    }

    public void markGenerating(OffsetDateTime now) {
        this.status = DocumentJobStatus.GENERATING;
        if (this.startedAt == null) {
            this.startedAt = now;
        }
        this.updatedAt = now;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void updateProgress(
            DocumentJobProgressStep progressStep,
            int progressPercent,
            String progressMessage,
            OffsetDateTime now
    ) {
        this.progressStep = progressStep;
        this.progressPercent = Math.max(0, Math.min(100, progressPercent));
        this.progressMessage = progressMessage;
        this.updatedAt = now;
    }

    public void markGenerated(OffsetDateTime now) {
        this.status = DocumentJobStatus.GENERATED;
        this.progressStep = DocumentJobProgressStep.GENERATED;
        this.progressPercent = 100;
        this.progressMessage = "문서 생성이 완료되었습니다.";
        this.completedAt = now;
        this.updatedAt = now;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markFailed(String errorCode, String errorMessage, OffsetDateTime now) {
        this.status = DocumentJobStatus.FAILED;
        this.progressStep = DocumentJobProgressStep.FAILED;
        this.progressMessage = errorMessage;
        this.completedAt = now;
        this.updatedAt = now;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public Long reportId() {
        return reportId;
    }

    public Long projectId() {
        return projectId;
    }

    public int reportRevision() {
        return reportRevision;
    }

    public Long templateId() {
        return templateId;
    }

    public DocumentJobStatus status() {
        return status;
    }

    public DocumentJobProgressStep progressStep() {
        return progressStep;
    }

    public int progressPercent() {
        return progressPercent;
    }

    public String progressMessage() {
        return progressMessage;
    }

    public Long requestedBy() {
        return requestedBy;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public DocumentWorkerType workerType() {
        return workerType;
    }

    public OutputFormat outputFormat() {
        return outputFormat;
    }

    public Map<String, Object> inputSnapshotJson() {
        return inputSnapshotJson;
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public OffsetDateTime requestedAt() {
        return requestedAt;
    }

    public OffsetDateTime startedAt() {
        return startedAt;
    }

    public OffsetDateTime completedAt() {
        return completedAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
