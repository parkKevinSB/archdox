package com.archdox.cloud.site.domain;

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

@Entity
@Table(name = "sites")
public class Site {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "site_code")
    private String siteCode;

    @Column(nullable = false)
    private String name;

    private String address;

    @Column(name = "site_type")
    private String siteType;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SiteStatus status = SiteStatus.ACTIVE;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Site() {
    }

    public Site(
            Long officeId,
            Long projectId,
            String siteCode,
            String name,
            String address,
            String siteType,
            LocalDate startDate,
            LocalDate endDate,
            Long createdBy,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.projectId = projectId;
        this.siteCode = siteCode;
        this.name = name;
        this.address = address;
        this.siteType = siteType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void archive(OffsetDateTime now) {
        this.status = SiteStatus.ARCHIVED;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public Long projectId() {
        return projectId;
    }

    public String siteCode() {
        return siteCode;
    }

    public String name() {
        return name;
    }

    public String address() {
        return address;
    }

    public String siteType() {
        return siteType;
    }

    public LocalDate startDate() {
        return startDate;
    }

    public LocalDate endDate() {
        return endDate;
    }

    public SiteStatus status() {
        return status;
    }
}
