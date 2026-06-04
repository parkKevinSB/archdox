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
@Table(name = "legal_acts")
public class LegalAct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "act_code", nullable = false)
    private String actCode;

    @Column(name = "act_name", nullable = false)
    private String actName;

    @Column(name = "act_type", nullable = false)
    private String actType;

    @Column(nullable = false)
    private String jurisdiction;

    @Column(name = "source_law_id")
    private String sourceLawId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LegalActStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected LegalAct() {
    }

    public LegalAct(
            Long sourceId,
            String actCode,
            String actName,
            String actType,
            String jurisdiction,
            String sourceLawId,
            OffsetDateTime now
    ) {
        this.sourceId = requireId(sourceId, "sourceId");
        this.actCode = required(actCode, "actCode");
        this.actName = required(actName, "actName");
        this.actType = required(actType, "actType");
        this.jurisdiction = required(jurisdiction, "jurisdiction");
        this.sourceLawId = blankToNull(sourceLawId);
        this.status = LegalActStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String actName, String actType, String jurisdiction, String sourceLawId, OffsetDateTime now) {
        this.actName = required(actName, "actName");
        this.actType = required(actType, "actType");
        this.jurisdiction = required(jurisdiction, "jurisdiction");
        this.sourceLawId = blankToNull(sourceLawId);
        this.status = LegalActStatus.ACTIVE;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long sourceId() {
        return sourceId;
    }

    public String actCode() {
        return actCode;
    }

    public String actName() {
        return actName;
    }

    public String actType() {
        return actType;
    }

    public String jurisdiction() {
        return jurisdiction;
    }

    public String sourceLawId() {
        return sourceLawId;
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
