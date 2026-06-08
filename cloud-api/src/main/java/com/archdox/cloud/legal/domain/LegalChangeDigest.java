package com.archdox.cloud.legal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "legal_change_digests")
public class LegalChangeDigest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "change_set_id", nullable = false, unique = true)
    private Long changeSetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LegalChangeDigestStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LegalChangeDigestSource source;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String summary;

    @Column(name = "impact_summary")
    private String impactSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "affected_report_types_json", nullable = false, columnDefinition = "jsonb")
    private List<String> affectedReportTypesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "affected_catalog_items_json", nullable = false, columnDefinition = "jsonb")
    private List<String> affectedCatalogItemsJson;

    @Column(name = "ai_harness_run_id")
    private String aiHarnessRunId;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected LegalChangeDigest() {
    }

    public LegalChangeDigest(
            Long changeSetId,
            LegalChangeDigestStatus status,
            LegalChangeDigestSource source,
            String title,
            String summary,
            String impactSummary,
            List<String> affectedReportTypes,
            List<String> affectedCatalogItems,
            String aiHarnessRunId,
            LocalDate effectiveDate,
            OffsetDateTime detectedAt,
            OffsetDateTime publishedAt,
            OffsetDateTime now
    ) {
        this.changeSetId = requireId(changeSetId, "changeSetId");
        this.status = status == null ? LegalChangeDigestStatus.DRAFT : status;
        this.source = source == null ? LegalChangeDigestSource.DETERMINISTIC : source;
        this.title = required(title, "title");
        this.summary = required(summary, "summary");
        this.impactSummary = blankToNull(impactSummary);
        this.affectedReportTypesJson = affectedReportTypes == null ? List.of() : List.copyOf(affectedReportTypes);
        this.affectedCatalogItemsJson = affectedCatalogItems == null ? List.of() : List.copyOf(affectedCatalogItems);
        this.aiHarnessRunId = blankToNull(aiHarnessRunId);
        this.effectiveDate = effectiveDate;
        this.detectedAt = detectedAt == null ? now : detectedAt;
        this.publishedAt = publishedAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long changeSetId() {
        return changeSetId;
    }

    public LegalChangeDigestStatus status() {
        return status;
    }

    public LegalChangeDigestSource source() {
        return source;
    }

    public String title() {
        return title;
    }

    public String summary() {
        return summary;
    }

    public String impactSummary() {
        return impactSummary;
    }

    public List<String> affectedReportTypes() {
        return affectedReportTypesJson == null ? List.of() : affectedReportTypesJson;
    }

    public List<String> affectedCatalogItems() {
        return affectedCatalogItemsJson == null ? List.of() : affectedCatalogItemsJson;
    }

    public String aiHarnessRunId() {
        return aiHarnessRunId;
    }

    public LocalDate effectiveDate() {
        return effectiveDate;
    }

    public OffsetDateTime detectedAt() {
        return detectedAt;
    }

    public OffsetDateTime publishedAt() {
        return publishedAt;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    public void publish(OffsetDateTime now) {
        this.status = LegalChangeDigestStatus.PUBLISHED;
        this.publishedAt = now;
        this.updatedAt = now;
    }

    public void refreshDeterministic(
            String title,
            String summary,
            String impactSummary,
            List<String> affectedReportTypes,
            List<String> affectedCatalogItems,
            LocalDate effectiveDate,
            OffsetDateTime detectedAt,
            OffsetDateTime now
    ) {
        if (this.source != LegalChangeDigestSource.DETERMINISTIC) {
            return;
        }
        this.status = LegalChangeDigestStatus.PUBLISHED;
        this.title = required(title, "title");
        this.summary = required(summary, "summary");
        this.impactSummary = blankToNull(impactSummary);
        this.affectedReportTypesJson = affectedReportTypes == null ? List.of() : List.copyOf(affectedReportTypes);
        this.affectedCatalogItemsJson = affectedCatalogItems == null ? List.of() : List.copyOf(affectedCatalogItems);
        this.effectiveDate = effectiveDate;
        this.detectedAt = detectedAt == null ? now : detectedAt;
        this.publishedAt = this.publishedAt == null ? now : this.publishedAt;
        this.updatedAt = now;
    }

    public void applyAiDraft(
            String title,
            String summary,
            String impactSummary,
            List<String> affectedReportTypes,
            List<String> affectedCatalogItems,
            String aiHarnessRunId,
            OffsetDateTime now
    ) {
        var appliedAt = now == null ? OffsetDateTime.now() : now;
        this.status = LegalChangeDigestStatus.PUBLISHED;
        this.source = LegalChangeDigestSource.AI;
        this.title = required(title, "title");
        this.summary = required(summary, "summary");
        this.impactSummary = blankToNull(impactSummary);
        this.affectedReportTypesJson = affectedReportTypes == null ? List.of() : List.copyOf(affectedReportTypes);
        this.affectedCatalogItemsJson = affectedCatalogItems == null ? List.of() : List.copyOf(affectedCatalogItems);
        this.aiHarnessRunId = blankToNull(aiHarnessRunId);
        this.publishedAt = appliedAt;
        this.updatedAt = appliedAt;
    }

    private static Long requireId(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
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
