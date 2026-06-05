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

@Entity
@Table(name = "legal_article_diffs")
public class LegalArticleDiff {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "change_set_id", nullable = false)
    private Long changeSetId;

    @Column(name = "article_id")
    private Long articleId;

    @Column(name = "article_key", nullable = false)
    private String articleKey;

    @Column(name = "article_no")
    private String articleNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false)
    private LegalArticleChangeType changeType;

    @Column(name = "before_article_version_id")
    private Long beforeArticleVersionId;

    @Column(name = "after_article_version_id")
    private Long afterArticleVersionId;

    @Column(name = "before_hash")
    private String beforeHash;

    @Column(name = "after_hash")
    private String afterHash;

    @Column(name = "diff_summary", nullable = false)
    private String diffSummary;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected LegalArticleDiff() {
    }

    public LegalArticleDiff(
            Long changeSetId,
            Long articleId,
            String articleKey,
            String articleNo,
            LegalArticleChangeType changeType,
            Long beforeArticleVersionId,
            Long afterArticleVersionId,
            String beforeHash,
            String afterHash,
            String diffSummary,
            OffsetDateTime now
    ) {
        this.changeSetId = requireId(changeSetId, "changeSetId");
        this.articleId = articleId;
        this.articleKey = required(articleKey, "articleKey");
        this.articleNo = blankToNull(articleNo);
        this.changeType = changeType == null ? LegalArticleChangeType.MODIFIED : changeType;
        this.beforeArticleVersionId = beforeArticleVersionId;
        this.afterArticleVersionId = afterArticleVersionId;
        this.beforeHash = blankToNull(beforeHash);
        this.afterHash = blankToNull(afterHash);
        this.diffSummary = required(diffSummary, "diffSummary");
        this.createdAt = now;
    }

    public Long id() {
        return id;
    }

    public Long changeSetId() {
        return changeSetId;
    }

    public Long articleId() {
        return articleId;
    }

    public String articleKey() {
        return articleKey;
    }

    public String articleNo() {
        return articleNo;
    }

    public LegalArticleChangeType changeType() {
        return changeType;
    }

    public Long beforeArticleVersionId() {
        return beforeArticleVersionId;
    }

    public Long afterArticleVersionId() {
        return afterArticleVersionId;
    }

    public String beforeHash() {
        return beforeHash;
    }

    public String afterHash() {
        return afterHash;
    }

    public String diffSummary() {
        return diffSummary;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
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
