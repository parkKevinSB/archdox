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
@Table(name = "legal_article_versions")
public class LegalArticleVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Column(name = "legal_version_id", nullable = false)
    private Long legalVersionId;

    @Column(name = "article_key", nullable = false)
    private String articleKey;

    @Column(name = "article_no", nullable = false)
    private String articleNo;

    @Column(name = "article_title")
    private String articleTitle;

    @Column(name = "article_text", nullable = false)
    private String articleText;

    @Column(name = "normalized_text", nullable = false)
    private String normalizedText;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_metadata_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> sourceMetadataJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected LegalArticleVersion() {
    }

    public LegalArticleVersion(
            Long articleId,
            Long legalVersionId,
            String articleKey,
            String articleNo,
            String articleTitle,
            String articleText,
            String normalizedText,
            String contentHash,
            LocalDate effectiveDate,
            Map<String, Object> sourceMetadataJson,
            OffsetDateTime now
    ) {
        this.articleId = requireId(articleId, "articleId");
        this.legalVersionId = requireId(legalVersionId, "legalVersionId");
        this.articleKey = required(articleKey, "articleKey");
        this.articleNo = required(articleNo, "articleNo");
        this.articleTitle = blankToNull(articleTitle);
        this.articleText = required(articleText, "articleText");
        this.normalizedText = required(normalizedText, "normalizedText");
        this.contentHash = required(contentHash, "contentHash");
        this.effectiveDate = effectiveDate;
        this.sourceMetadataJson = sourceMetadataJson == null ? Map.of() : Map.copyOf(sourceMetadataJson);
        this.createdAt = now;
    }

    public Long id() {
        return id;
    }

    public Long articleId() {
        return articleId;
    }

    public Long legalVersionId() {
        return legalVersionId;
    }

    public String articleKey() {
        return articleKey;
    }

    public String articleNo() {
        return articleNo;
    }

    public String articleTitle() {
        return articleTitle;
    }

    public String articleText() {
        return articleText;
    }

    public String contentHash() {
        return contentHash;
    }

    public LocalDate effectiveDate() {
        return effectiveDate;
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
