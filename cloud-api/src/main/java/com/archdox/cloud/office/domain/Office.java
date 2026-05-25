package com.archdox.cloud.office.domain;

import com.archdox.shared.OfficeStatus;
import com.archdox.shared.OfficeType;
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
@Table(name = "offices")
public class Office {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_code", nullable = false, unique = true)
    private String officeCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfficeType type;

    @Column(name = "plan_code", nullable = false)
    private String planCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfficeStatus status = OfficeStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Office() {
    }

    public Office(String officeCode, String displayName, OfficeType type, String planCode, OffsetDateTime now) {
        this.officeCode = officeCode;
        this.displayName = displayName;
        this.type = type;
        this.planCode = planCode;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public String officeCode() {
        return officeCode;
    }

    public String displayName() {
        return displayName;
    }

    public OfficeType type() {
        return type;
    }

    public String planCode() {
        return planCode;
    }

    public OfficeStatus status() {
        return status;
    }
}
