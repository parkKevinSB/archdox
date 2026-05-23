package com.archdox.cloud.assignment.domain;

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
@Table(name = "project_assignments")
public class ProjectAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectAssignmentRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status;

    @Column(name = "assigned_by")
    private Long assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ProjectAssignment() {
    }

    public ProjectAssignment(
            Long officeId,
            Long projectId,
            Long userId,
            ProjectAssignmentRole role,
            Long assignedBy,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.projectId = projectId;
        this.userId = userId;
        this.role = role;
        this.status = AssignmentStatus.ACTIVE;
        this.assignedBy = assignedBy;
        this.assignedAt = now;
        this.updatedAt = now;
    }

    public void update(ProjectAssignmentRole role, Long assignedBy, OffsetDateTime now) {
        this.role = role;
        this.status = AssignmentStatus.ACTIVE;
        this.assignedBy = assignedBy;
        this.assignedAt = now;
        this.updatedAt = now;
    }

    public void remove(Long actorUserId, OffsetDateTime now) {
        this.status = AssignmentStatus.REMOVED;
        this.assignedBy = actorUserId;
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

    public Long userId() {
        return userId;
    }

    public ProjectAssignmentRole role() {
        return role;
    }

    public AssignmentStatus status() {
        return status;
    }

    public Long assignedBy() {
        return assignedBy;
    }

    public OffsetDateTime assignedAt() {
        return assignedAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }
}
