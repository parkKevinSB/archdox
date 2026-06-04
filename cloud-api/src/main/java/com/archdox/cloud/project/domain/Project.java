package com.archdox.cloud.project.domain;

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
@Table(name = "projects")
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(nullable = false)
    private String name;

    private String address;

    @Column(name = "building_type")
    private String buildingType;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status = ProjectStatus.ACTIVE;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Project() {
    }

    public Project(
            Long officeId,
            String name,
            String address,
            String buildingType,
            LocalDate startDate,
            LocalDate endDate,
            Long createdBy,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.name = name;
        this.address = address;
        this.buildingType = buildingType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void archive(OffsetDateTime now) {
        this.status = ProjectStatus.ARCHIVED;
        this.updatedAt = now;
    }

    public void updateDetails(
            String name,
            String address,
            String buildingType,
            LocalDate startDate,
            LocalDate endDate,
            OffsetDateTime now
    ) {
        this.name = name;
        this.address = address;
        this.buildingType = buildingType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public String name() {
        return name;
    }

    public String address() {
        return address;
    }

    public String buildingType() {
        return buildingType;
    }

    public LocalDate startDate() {
        return startDate;
    }

    public LocalDate endDate() {
        return endDate;
    }

    public ProjectStatus status() {
        return status;
    }
}
