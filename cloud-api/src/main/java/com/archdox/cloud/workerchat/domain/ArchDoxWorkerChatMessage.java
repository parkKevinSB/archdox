package com.archdox.cloud.workerchat.domain;

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
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "archdox_worker_chat_messages")
public class ArchDoxWorkerChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArchDoxWorkerChatMessageRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArchDoxWorkerChatMessageStatus status;

    @Column(nullable = false)
    private String content;

    @Column(name = "worker_request_id")
    private UUID workerRequestId;

    @Column(name = "worker_action_type")
    private String workerActionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadataJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ArchDoxWorkerChatMessage() {
    }

    public ArchDoxWorkerChatMessage(
            Long officeId,
            Long sessionId,
            Long userId,
            ArchDoxWorkerChatMessageRole role,
            ArchDoxWorkerChatMessageStatus status,
            String content,
            UUID workerRequestId,
            String workerActionType,
            Map<String, Object> metadataJson,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.role = role;
        this.status = status;
        this.content = content;
        this.workerRequestId = workerRequestId;
        this.workerActionType = workerActionType;
        this.metadataJson = metadataJson == null ? Map.of() : Map.copyOf(metadataJson);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void complete(String content, Map<String, Object> metadataJson, OffsetDateTime now) {
        this.status = ArchDoxWorkerChatMessageStatus.COMPLETED;
        this.content = content;
        this.metadataJson = metadataJson == null ? Map.of() : Map.copyOf(metadataJson);
        this.updatedAt = now;
    }

    public void fail(String content, Map<String, Object> metadataJson, OffsetDateTime now) {
        this.status = ArchDoxWorkerChatMessageStatus.FAILED;
        this.content = content;
        this.metadataJson = metadataJson == null ? Map.of() : Map.copyOf(metadataJson);
        this.updatedAt = now;
    }

    public void cancel(String content, Map<String, Object> metadataJson, OffsetDateTime now) {
        this.status = ArchDoxWorkerChatMessageStatus.CANCELLED;
        this.content = content;
        this.metadataJson = metadataJson == null ? Map.of() : Map.copyOf(metadataJson);
        this.updatedAt = now;
    }

    public void reviseCompleted(String content, Map<String, Object> metadataJson, OffsetDateTime now) {
        if (this.status != ArchDoxWorkerChatMessageStatus.COMPLETED) {
            return;
        }
        this.content = content;
        this.metadataJson = metadataJson == null ? Map.of() : Map.copyOf(metadataJson);
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public Long sessionId() {
        return sessionId;
    }

    public Long userId() {
        return userId;
    }

    public ArchDoxWorkerChatMessageRole role() {
        return role;
    }

    public ArchDoxWorkerChatMessageStatus status() {
        return status;
    }

    public String content() {
        return content;
    }

    public UUID workerRequestId() {
        return workerRequestId;
    }

    public String workerActionType() {
        return workerActionType;
    }

    public Map<String, Object> metadataJson() {
        return metadataJson;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }
}
