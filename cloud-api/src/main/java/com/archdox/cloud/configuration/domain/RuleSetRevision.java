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
@Table(name = "rule_set_revisions")
public class RuleSetRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_set_id", nullable = false)
    private RuleSet ruleSet;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConfigRevisionStatus status = ConfigRevisionStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> rulesJson;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "published_by")
    private Long publishedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    protected RuleSetRevision() {
    }

    public RuleSetRevision(RuleSet ruleSet, int version, Map<String, Object> rulesJson, Long createdBy, OffsetDateTime now) {
        this.ruleSet = ruleSet;
        this.version = version;
        this.rulesJson = rulesJson == null ? Map.of() : Map.copyOf(rulesJson);
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

    public RuleSet ruleSet() {
        return ruleSet;
    }

    public int version() {
        return version;
    }

    public ConfigRevisionStatus status() {
        return status;
    }

    public Map<String, Object> rulesJson() {
        return rulesJson;
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
