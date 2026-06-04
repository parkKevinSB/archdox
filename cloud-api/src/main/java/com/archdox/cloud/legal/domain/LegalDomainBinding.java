package com.archdox.cloud.legal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "legal_domain_bindings")
public class LegalDomainBinding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "binding_scope", nullable = false)
    private String bindingScope;

    @Column(name = "binding_key", nullable = false)
    private String bindingKey;

    @Column(name = "act_id", nullable = false)
    private Long actId;

    @Column(name = "article_id")
    private Long articleId;

    @Column(name = "report_type")
    private String reportType;

    @Column(name = "catalog_code")
    private String catalogCode;

    @Column(name = "catalog_version")
    private Integer catalogVersion;

    @Column(name = "checklist_item_code")
    private String checklistItemCode;

    @Column(nullable = false)
    private String relevance;

    @Column(nullable = false)
    private String status;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadataJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected LegalDomainBinding() {
    }

    public Long id() {
        return id;
    }

    public String bindingScope() {
        return bindingScope;
    }

    public String bindingKey() {
        return bindingKey;
    }

    public Long actId() {
        return actId;
    }

    public Long articleId() {
        return articleId;
    }

    public String reportType() {
        return reportType;
    }

    public String catalogCode() {
        return catalogCode;
    }

    public Integer catalogVersion() {
        return catalogVersion;
    }

    public String checklistItemCode() {
        return checklistItemCode;
    }

    public String relevance() {
        return relevance;
    }

    public String status() {
        return status;
    }

    public LocalDate effectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate effectiveTo() {
        return effectiveTo;
    }

    public String notes() {
        return notes;
    }

    public Map<String, Object> metadataJson() {
        return metadataJson == null ? Map.of() : Map.copyOf(metadataJson);
    }
}
