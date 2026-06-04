package com.archdox.cloud.engine.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "engine_review_sessions")
public class EngineReviewSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_session_id", nullable = false, unique = true)
    private String externalSessionId;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "office_id")
    private Long officeId;

    @Column(name = "customer_project_ref")
    private String customerProjectRef;

    @Column(name = "review_purpose", nullable = false)
    private String reviewPurpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EngineReviewSessionStatus status;

    @Column(name = "document_type_hint")
    private String documentTypeHint;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "document_text", columnDefinition = "text")
    private String documentText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "facts_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> factsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalized_context_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> normalizedContextJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_result_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> validationResultJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "normalized_at")
    private OffsetDateTime normalizedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    protected EngineReviewSession() {
    }

    public EngineReviewSession(
            String externalSessionId,
            Long ownerUserId,
            Long officeId,
            String customerProjectRef,
            String reviewPurpose,
            OffsetDateTime now
    ) {
        this.externalSessionId = required(externalSessionId, "externalSessionId");
        this.ownerUserId = ownerUserId;
        this.officeId = officeId;
        this.customerProjectRef = blankToNull(customerProjectRef);
        this.reviewPurpose = required(reviewPurpose, "reviewPurpose");
        this.status = EngineReviewSessionStatus.CREATED;
        this.factsJson = Map.of("facts", List.of());
        this.normalizedContextJson = Map.of();
        this.validationResultJson = Map.of();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void submitDocument(String documentTypeHint, String fileName, String documentText, OffsetDateTime now) {
        this.documentTypeHint = blankToNull(documentTypeHint);
        this.fileName = blankToNull(fileName);
        this.documentText = required(documentText, "documentText");
        this.status = EngineReviewSessionStatus.DOCUMENT_RECEIVED;
        this.updatedAt = now;
    }

    public void submitFacts(List<Map<String, Object>> facts, OffsetDateTime now) {
        this.factsJson = Map.of("facts", facts == null ? List.of() : List.copyOf(facts));
        if (this.status == EngineReviewSessionStatus.CREATED) {
            this.status = EngineReviewSessionStatus.FACTS_RECEIVED;
        }
        this.updatedAt = now;
    }

    public void normalize(Map<String, Object> normalizedContextJson, OffsetDateTime now) {
        this.normalizedContextJson = normalizedContextJson == null ? Map.of() : Map.copyOf(normalizedContextJson);
        this.normalizedAt = now;
        this.status = EngineReviewSessionStatus.NORMALIZED;
        this.updatedAt = now;
    }

    public void validate(Map<String, Object> validationResultJson, OffsetDateTime now) {
        this.validationResultJson = validationResultJson == null ? Map.of() : Map.copyOf(validationResultJson);
        this.status = EngineReviewSessionStatus.VALIDATED;
        this.completedAt = now;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public String externalSessionId() {
        return externalSessionId;
    }

    public Long ownerUserId() {
        return ownerUserId;
    }

    public Long officeId() {
        return officeId;
    }

    public String customerProjectRef() {
        return customerProjectRef;
    }

    public String reviewPurpose() {
        return reviewPurpose;
    }

    public EngineReviewSessionStatus status() {
        return status;
    }

    public String documentTypeHint() {
        return documentTypeHint;
    }

    public String fileName() {
        return fileName;
    }

    public String documentText() {
        return documentText;
    }

    public Map<String, Object> factsJson() {
        return factsJson;
    }

    public Map<String, Object> normalizedContextJson() {
        return normalizedContextJson;
    }

    public Map<String, Object> validationResultJson() {
        return validationResultJson;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    public OffsetDateTime normalizedAt() {
        return normalizedAt;
    }

    public OffsetDateTime completedAt() {
        return completedAt;
    }

    private static String required(String value, String fieldName) {
        var normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
