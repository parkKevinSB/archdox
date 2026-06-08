package com.archdox.cloud.legal.domain;

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
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "legal_digest_ai_drafts")
public class LegalDigestAiDraft {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "digest_id", nullable = false)
    private Long digestId;

    @Column(name = "change_set_id", nullable = false)
    private Long changeSetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LegalDigestAiDraftStatus status;

    @Column(name = "worker_request_id", nullable = false)
    private UUID workerRequestId;

    @Column(name = "worker_status", nullable = false)
    private String workerStatus;

    @Column(name = "result_code")
    private String resultCode;

    @Column(name = "ai_harness_run_id")
    private String aiHarnessRunId;

    @Column(name = "digest_draft_status")
    private String digestDraftStatus;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String summary;

    @Column(name = "impact_summary")
    private String impactSummary;

    @Column(name = "confidence")
    private String confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "affected_report_types_json", nullable = false, columnDefinition = "jsonb")
    private List<String> affectedReportTypesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "affected_catalog_items_json", nullable = false, columnDefinition = "jsonb")
    private List<String> affectedCatalogItemsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_articles_json", nullable = false, columnDefinition = "jsonb")
    private List<String> keyArticlesJson;

    @Column(name = "review_notes")
    private String reviewNotes;

    @Column(name = "publication_applied", nullable = false)
    private boolean publicationApplied;

    @Column(name = "corpus_mutated", nullable = false)
    private boolean corpusMutated;

    @Column(name = "digest_mutated", nullable = false)
    private boolean digestMutated;

    @Column(name = "generated_by_user_id", nullable = false)
    private Long generatedByUserId;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "applied_by_user_id")
    private Long appliedByUserId;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected LegalDigestAiDraft() {
    }

    public LegalDigestAiDraft(
            Long digestId,
            Long changeSetId,
            UUID workerRequestId,
            String workerStatus,
            String resultCode,
            String aiHarnessRunId,
            String digestDraftStatus,
            String title,
            String summary,
            String impactSummary,
            String confidence,
            List<String> affectedReportTypes,
            List<String> affectedCatalogItems,
            List<String> keyArticles,
            String reviewNotes,
            boolean publicationApplied,
            boolean corpusMutated,
            boolean digestMutated,
            Long generatedByUserId,
            OffsetDateTime now
    ) {
        this.digestId = requireId(digestId, "digestId");
        this.changeSetId = requireId(changeSetId, "changeSetId");
        this.status = LegalDigestAiDraftStatus.NEEDS_HUMAN_REVIEW;
        this.workerRequestId = workerRequestId == null ? UUID.randomUUID() : workerRequestId;
        this.workerStatus = required(workerStatus, "workerStatus");
        this.resultCode = blankToNull(resultCode);
        this.aiHarnessRunId = blankToNull(aiHarnessRunId);
        this.digestDraftStatus = blankToNull(digestDraftStatus);
        this.title = required(title, "title");
        this.summary = required(summary, "summary");
        this.impactSummary = blankToNull(impactSummary);
        this.confidence = blankToNull(confidence);
        this.affectedReportTypesJson = affectedReportTypes == null ? List.of() : List.copyOf(affectedReportTypes);
        this.affectedCatalogItemsJson = affectedCatalogItems == null ? List.of() : List.copyOf(affectedCatalogItems);
        this.keyArticlesJson = keyArticles == null ? List.of() : List.copyOf(keyArticles);
        this.reviewNotes = blankToNull(reviewNotes);
        this.publicationApplied = publicationApplied;
        this.corpusMutated = corpusMutated;
        this.digestMutated = digestMutated;
        this.generatedByUserId = requireId(generatedByUserId, "generatedByUserId");
        this.generatedAt = now == null ? OffsetDateTime.now() : now;
        this.createdAt = this.generatedAt;
        this.updatedAt = this.generatedAt;
    }

    public void approve(Long userId, OffsetDateTime now) {
        if (status != LegalDigestAiDraftStatus.NEEDS_HUMAN_REVIEW && status != LegalDigestAiDraftStatus.GENERATED) {
            throw new IllegalStateException("Legal digest AI draft is not awaiting human review");
        }
        var reviewedAtValue = now == null ? OffsetDateTime.now() : now;
        this.status = LegalDigestAiDraftStatus.APPROVED;
        this.reviewedByUserId = requireId(userId, "userId");
        this.reviewedAt = reviewedAtValue;
        this.updatedAt = reviewedAtValue;
    }

    public void reject(Long userId, OffsetDateTime now) {
        if (status != LegalDigestAiDraftStatus.NEEDS_HUMAN_REVIEW && status != LegalDigestAiDraftStatus.GENERATED) {
            throw new IllegalStateException("Legal digest AI draft is not awaiting human review");
        }
        var reviewedAtValue = now == null ? OffsetDateTime.now() : now;
        this.status = LegalDigestAiDraftStatus.REJECTED;
        this.reviewedByUserId = requireId(userId, "userId");
        this.reviewedAt = reviewedAtValue;
        this.updatedAt = reviewedAtValue;
    }

    public void apply(Long userId, OffsetDateTime now) {
        if (status != LegalDigestAiDraftStatus.APPROVED) {
            throw new IllegalStateException("Legal digest AI draft is not approved");
        }
        var appliedAtValue = now == null ? OffsetDateTime.now() : now;
        this.status = LegalDigestAiDraftStatus.APPLIED;
        this.appliedByUserId = requireId(userId, "userId");
        this.appliedAt = appliedAtValue;
        this.updatedAt = appliedAtValue;
    }

    public Long id() {
        return id;
    }

    public Long digestId() {
        return digestId;
    }

    public Long changeSetId() {
        return changeSetId;
    }

    public LegalDigestAiDraftStatus status() {
        return status;
    }

    public UUID workerRequestId() {
        return workerRequestId;
    }

    public String workerStatus() {
        return workerStatus;
    }

    public String resultCode() {
        return resultCode;
    }

    public String aiHarnessRunId() {
        return aiHarnessRunId;
    }

    public String digestDraftStatus() {
        return digestDraftStatus;
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

    public String confidence() {
        return confidence;
    }

    public List<String> affectedReportTypes() {
        return affectedReportTypesJson == null ? List.of() : affectedReportTypesJson;
    }

    public List<String> affectedCatalogItems() {
        return affectedCatalogItemsJson == null ? List.of() : affectedCatalogItemsJson;
    }

    public List<String> keyArticles() {
        return keyArticlesJson == null ? List.of() : keyArticlesJson;
    }

    public String reviewNotes() {
        return reviewNotes;
    }

    public boolean publicationApplied() {
        return publicationApplied;
    }

    public boolean corpusMutated() {
        return corpusMutated;
    }

    public boolean digestMutated() {
        return digestMutated;
    }

    public Long generatedByUserId() {
        return generatedByUserId;
    }

    public Long reviewedByUserId() {
        return reviewedByUserId;
    }

    public OffsetDateTime reviewedAt() {
        return reviewedAt;
    }

    public Long appliedByUserId() {
        return appliedByUserId;
    }

    public OffsetDateTime generatedAt() {
        return generatedAt;
    }

    public OffsetDateTime appliedAt() {
        return appliedAt;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
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
