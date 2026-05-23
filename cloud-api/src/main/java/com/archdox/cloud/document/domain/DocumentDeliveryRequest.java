package com.archdox.cloud.document.domain;

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
@Table(name = "document_delivery_requests")
public class DocumentDeliveryRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "document_job_id", nullable = false)
    private Long documentJobId;

    @Column(name = "artifact_id")
    private Long artifactId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentDeliveryChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentDeliveryStatus status;

    @Column(name = "recipient_ref")
    private String recipientRef;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "prepared_storage_kind")
    private String preparedStorageKind;

    @Column(name = "prepared_storage_ref")
    private String preparedStorageRef;

    @Column(name = "prepared_expires_at")
    private OffsetDateTime preparedExpiresAt;

    @Column(name = "download_ready_at")
    private OffsetDateTime downloadReadyAt;

    @Column(name = "agent_command_id")
    private Long agentCommandId;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DocumentDeliveryRequest() {
    }

    public DocumentDeliveryRequest(
            Long officeId,
            Long documentJobId,
            Long artifactId,
            DocumentDeliveryChannel channel,
            String recipientRef,
            Long requestedBy,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.documentJobId = documentJobId;
        this.artifactId = artifactId;
        this.channel = channel;
        this.status = DocumentDeliveryStatus.REQUESTED;
        this.recipientRef = recipientRef;
        this.requestedBy = requestedBy;
        this.requestedAt = now;
        this.updatedAt = now;
    }

    public void markSending(OffsetDateTime now) {
        this.status = DocumentDeliveryStatus.SENDING;
        this.updatedAt = now;
    }

    public void markSending(Long agentCommandId, OffsetDateTime now) {
        this.agentCommandId = agentCommandId;
        markSending(now);
    }

    public void markCompleted(OffsetDateTime now) {
        this.status = DocumentDeliveryStatus.COMPLETED;
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void markPrepared(
            String preparedStorageKind,
            String preparedStorageRef,
            OffsetDateTime preparedExpiresAt,
            OffsetDateTime now
    ) {
        this.preparedStorageKind = preparedStorageKind;
        this.preparedStorageRef = preparedStorageRef;
        this.preparedExpiresAt = preparedExpiresAt;
        this.downloadReadyAt = now;
        markCompleted(now);
    }

    public void markFailed(String errorMessage, OffsetDateTime now) {
        if (status == DocumentDeliveryStatus.COMPLETED) {
            return;
        }
        this.status = DocumentDeliveryStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public Long documentJobId() {
        return documentJobId;
    }

    public Long artifactId() {
        return artifactId;
    }

    public DocumentDeliveryChannel channel() {
        return channel;
    }

    public DocumentDeliveryStatus status() {
        return status;
    }

    public String recipientRef() {
        return recipientRef;
    }

    public Long requestedBy() {
        return requestedBy;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public String preparedStorageKind() {
        return preparedStorageKind;
    }

    public String preparedStorageRef() {
        return preparedStorageRef;
    }

    public OffsetDateTime preparedExpiresAt() {
        return preparedExpiresAt;
    }

    public OffsetDateTime downloadReadyAt() {
        return downloadReadyAt;
    }

    public Long agentCommandId() {
        return agentCommandId;
    }

    public OffsetDateTime requestedAt() {
        return requestedAt;
    }

    public OffsetDateTime completedAt() {
        return completedAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }
}
