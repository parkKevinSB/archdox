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
@Table(name = "workflow_definition_revisions")
public class WorkflowDefinitionRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    private WorkflowDefinition definition;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConfigRevisionStatus status = ConfigRevisionStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> definitionJson;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "published_by")
    private Long publishedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    protected WorkflowDefinitionRevision() {
    }

    public WorkflowDefinitionRevision(WorkflowDefinition definition, int version, Map<String, Object> definitionJson, Long createdBy, OffsetDateTime now) {
        this.definition = definition;
        this.version = version;
        this.definitionJson = definitionJson == null ? Map.of() : Map.copyOf(definitionJson);
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public void publish(Long publishedBy, OffsetDateTime now) {
        this.status = ConfigRevisionStatus.PUBLISHED;
        this.publishedBy = publishedBy;
        this.publishedAt = now;
    }

    public Long id() {
        return id;
    }

    public WorkflowDefinition definition() {
        return definition;
    }

    public int version() {
        return version;
    }

    public ConfigRevisionStatus status() {
        return status;
    }

    public Map<String, Object> definitionJson() {
        return definitionJson;
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
}
