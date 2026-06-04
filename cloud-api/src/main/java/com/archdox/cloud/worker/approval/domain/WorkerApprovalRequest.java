package com.archdox.cloud.worker.approval.domain;

import com.archdox.worker.domain.ArchDoxWorkerActionOrigin;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.domain.ArchDoxWorkerRequestSource;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "worker_approval_requests")
public class WorkerApprovalRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id")
    private Long officeId;

    @Column(name = "worker_request_id", nullable = false)
    private UUID workerRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_source", nullable = false)
    private ArchDoxWorkerRequestSource requestSource;

    @Column(name = "command")
    private String command;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "document_job_id")
    private Long documentJobId;

    @Column(name = "locale", nullable = false)
    private String locale;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ArchDoxWorkerActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_origin", nullable = false)
    private ArchDoxWorkerActionOrigin actionOrigin;

    @Column(name = "action_reason")
    private String actionReason;

    @Column(name = "confidence", nullable = false)
    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_payload_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> actionPayloadJson;

    @Column(name = "decision_code")
    private String decisionCode;

    @Column(name = "decision_message")
    private String decisionMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkerApprovalRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "decided_by_user_id")
    private Long decidedByUserId;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "execution_request_id")
    private UUID executionRequestId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected WorkerApprovalRequest() {
    }

    public WorkerApprovalRequest(
            Long officeId,
            UUID workerRequestId,
            ArchDoxWorkerRequestSource requestSource,
            String command,
            Long userId,
            Long projectId,
            Long siteId,
            Long reportId,
            Long documentJobId,
            String locale,
            ArchDoxWorkerActionType actionType,
            ArchDoxWorkerActionOrigin actionOrigin,
            String actionReason,
            double confidence,
            Map<String, Object> actionPayloadJson,
            String decisionCode,
            String decisionMessage,
            OffsetDateTime requestedAt,
            OffsetDateTime expiresAt,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.workerRequestId = workerRequestId;
        this.requestSource = requestSource == null ? ArchDoxWorkerRequestSource.SYSTEM : requestSource;
        this.command = blankToNull(command);
        this.userId = userId;
        this.projectId = projectId;
        this.siteId = siteId;
        this.reportId = reportId;
        this.documentJobId = documentJobId;
        this.locale = locale == null || locale.isBlank() ? "ko-KR" : locale.trim();
        this.actionType = actionType;
        this.actionOrigin = actionOrigin == null ? ArchDoxWorkerActionOrigin.SYSTEM : actionOrigin;
        this.actionReason = blankToNull(actionReason);
        this.confidence = BigDecimal.valueOf(confidence);
        this.actionPayloadJson = copyPayload(actionPayloadJson);
        this.decisionCode = blankToNull(decisionCode);
        this.decisionMessage = blankToNull(decisionMessage);
        this.status = WorkerApprovalRequestStatus.PENDING;
        this.requestedAt = requestedAt == null ? now : requestedAt;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void approve(Long decidedByUserId, String reason, UUID executionRequestId, OffsetDateTime now) {
        requirePending();
        this.status = WorkerApprovalRequestStatus.APPROVED;
        this.decidedByUserId = decidedByUserId;
        this.decisionReason = blankToNull(reason);
        this.decidedAt = now;
        this.executionRequestId = executionRequestId;
        this.updatedAt = now;
    }

    public void reject(Long decidedByUserId, String reason, OffsetDateTime now) {
        requirePending();
        this.status = WorkerApprovalRequestStatus.REJECTED;
        this.decidedByUserId = decidedByUserId;
        this.decisionReason = blankToNull(reason);
        this.decidedAt = now;
        this.updatedAt = now;
    }

    public boolean isApprovedFor(UUID executionRequestId, ArchDoxWorkerActionType actionType) {
        return status == WorkerApprovalRequestStatus.APPROVED
                && this.executionRequestId != null
                && this.executionRequestId.equals(executionRequestId)
                && this.actionType == actionType;
    }

    public String workflowKey() {
        return "worker-approval:" + (id == null ? workerRequestId : id);
    }

    private void requirePending() {
        if (status != WorkerApprovalRequestStatus.PENDING) {
            throw new IllegalStateException("Worker approval request is not pending");
        }
    }

    private Map<String, Object> copyPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, Object>();
        payload.forEach((key, value) -> {
            if (key != null && value != null) {
                copy.put(key, value);
            }
        });
        return Map.copyOf(copy);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public UUID workerRequestId() {
        return workerRequestId;
    }

    public ArchDoxWorkerRequestSource requestSource() {
        return requestSource;
    }

    public String command() {
        return command;
    }

    public Long userId() {
        return userId;
    }

    public Long projectId() {
        return projectId;
    }

    public Long siteId() {
        return siteId;
    }

    public Long reportId() {
        return reportId;
    }

    public Long documentJobId() {
        return documentJobId;
    }

    public String locale() {
        return locale;
    }

    public ArchDoxWorkerActionType actionType() {
        return actionType;
    }

    public ArchDoxWorkerActionOrigin actionOrigin() {
        return actionOrigin;
    }

    public String actionReason() {
        return actionReason;
    }

    public double confidence() {
        return confidence == null ? 1.0d : confidence.doubleValue();
    }

    public Map<String, Object> actionPayloadJson() {
        return actionPayloadJson;
    }

    public String decisionCode() {
        return decisionCode;
    }

    public String decisionMessage() {
        return decisionMessage;
    }

    public WorkerApprovalRequestStatus status() {
        return status;
    }

    public OffsetDateTime requestedAt() {
        return requestedAt;
    }

    public OffsetDateTime expiresAt() {
        return expiresAt;
    }

    public Long decidedByUserId() {
        return decidedByUserId;
    }

    public String decisionReason() {
        return decisionReason;
    }

    public OffsetDateTime decidedAt() {
        return decidedAt;
    }

    public UUID executionRequestId() {
        return executionRequestId;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }
}
