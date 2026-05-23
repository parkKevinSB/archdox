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
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "document_template_revisions")
public class DocumentTemplateRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private DocumentTemplate template;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConfigRevisionStatus status = ConfigRevisionStatus.DRAFT;

    @Column(name = "template_storage_kind")
    private String templateStorageKind;

    @Column(name = "template_storage_ref")
    private String templateStorageRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> schemaJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "compose_policy_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> composePolicyJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_prompts_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> aiPromptsJson;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "published_by")
    private Long publishedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    protected DocumentTemplateRevision() {
    }

    public DocumentTemplateRevision(
            DocumentTemplate template,
            int version,
            String templateStorageKind,
            String templateStorageRef,
            Map<String, Object> schemaJson,
            Map<String, Object> composePolicyJson,
            Map<String, Object> aiPromptsJson,
            Long createdBy,
            OffsetDateTime now
    ) {
        this.template = template;
        this.version = version;
        this.templateStorageKind = templateStorageKind;
        this.templateStorageRef = templateStorageRef;
        this.schemaJson = safeMap(schemaJson);
        this.composePolicyJson = safeMap(composePolicyJson);
        this.aiPromptsJson = safeMap(aiPromptsJson);
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public void publish(Long publishedBy, OffsetDateTime now) {
        this.status = ConfigRevisionStatus.PUBLISHED;
        this.publishedBy = publishedBy;
        this.publishedAt = now;
    }

    public void attachTemplateContent(String templateStorageKind, String templateStorageRef) {
        this.templateStorageKind = templateStorageKind;
        this.templateStorageRef = templateStorageRef;
    }

    public Long id() {
        return id;
    }

    public DocumentTemplate template() {
        return template;
    }

    public int version() {
        return version;
    }

    public ConfigRevisionStatus status() {
        return status;
    }

    public String templateStorageKind() {
        return templateStorageKind;
    }

    public String templateStorageRef() {
        return templateStorageRef;
    }

    public Map<String, Object> schemaJson() {
        return schemaJson;
    }

    public Map<String, Object> composePolicyJson() {
        return composePolicyJson;
    }

    public Map<String, Object> aiPromptsJson() {
        return aiPromptsJson;
    }

    public Long createdBy() {
        return createdBy;
    }

    public Long publishedBy() {
        return publishedBy;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime publishedAt() {
        return publishedAt;
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : Map.copyOf(value);
    }
}
