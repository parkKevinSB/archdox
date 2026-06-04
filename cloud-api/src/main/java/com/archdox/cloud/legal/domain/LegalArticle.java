package com.archdox.cloud.legal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "legal_articles")
public class LegalArticle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "act_id", nullable = false)
    private Long actId;

    @Column(name = "article_key", nullable = false)
    private String articleKey;

    @Column(name = "article_no", nullable = false)
    private String articleNo;

    @Column(name = "article_title")
    private String articleTitle;

    @Column(name = "parent_article_key")
    private String parentArticleKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected LegalArticle() {
    }

    public LegalArticle(
            Long actId,
            String articleKey,
            String articleNo,
            String articleTitle,
            String parentArticleKey,
            int sortOrder,
            OffsetDateTime now
    ) {
        this.actId = requireId(actId, "actId");
        this.articleKey = required(articleKey, "articleKey");
        this.articleNo = required(articleNo, "articleNo");
        this.articleTitle = blankToNull(articleTitle);
        this.parentArticleKey = blankToNull(parentArticleKey);
        this.sortOrder = sortOrder;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String articleNo, String articleTitle, String parentArticleKey, int sortOrder, OffsetDateTime now) {
        this.articleNo = required(articleNo, "articleNo");
        this.articleTitle = blankToNull(articleTitle);
        this.parentArticleKey = blankToNull(parentArticleKey);
        this.sortOrder = sortOrder;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long actId() {
        return actId;
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

    public int sortOrder() {
        return sortOrder;
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
