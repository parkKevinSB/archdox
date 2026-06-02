package com.archdox.cloud.workerchat.domain;

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
@Table(name = "archdox_worker_chat_sessions")
public class ArchDoxWorkerChatSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArchDoxWorkerChatSessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArchDoxWorkerChatStage stage;

    @Column(nullable = false)
    private String title;

    @Column(name = "last_message_at")
    private OffsetDateTime lastMessageAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ArchDoxWorkerChatSession() {
    }

    public ArchDoxWorkerChatSession(
            Long officeId,
            Long projectId,
            Long userId,
            String title,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.projectId = projectId;
        this.userId = userId;
        this.status = ArchDoxWorkerChatSessionStatus.ACTIVE;
        this.stage = ArchDoxWorkerChatStage.AWAITING_SITE;
        this.title = title;
        this.lastMessageAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void touch(OffsetDateTime now) {
        this.lastMessageAt = now;
        this.updatedAt = now;
    }

    public void selectSite(Long siteId, OffsetDateTime now) {
        this.siteId = siteId;
        this.reportId = null;
        this.stage = ArchDoxWorkerChatStage.AWAITING_REPORT;
        this.updatedAt = now;
    }

    public void selectReport(Long reportId, OffsetDateTime now) {
        this.reportId = reportId;
        this.stage = ArchDoxWorkerChatStage.REPORT_WORKING;
        this.updatedAt = now;
    }

    public void moveTo(ArchDoxWorkerChatStage stage, OffsetDateTime now) {
        this.stage = stage;
        this.updatedAt = now;
    }

    public void complete(OffsetDateTime now) {
        this.status = ArchDoxWorkerChatSessionStatus.COMPLETED;
        this.stage = ArchDoxWorkerChatStage.COMPLETED;
        this.completedAt = now;
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

    public Long siteId() {
        return siteId;
    }

    public Long reportId() {
        return reportId;
    }

    public Long userId() {
        return userId;
    }

    public ArchDoxWorkerChatSessionStatus status() {
        return status;
    }

    public ArchDoxWorkerChatStage stage() {
        return stage;
    }

    public String title() {
        return title;
    }

    public OffsetDateTime lastMessageAt() {
        return lastMessageAt;
    }

    public OffsetDateTime completedAt() {
        return completedAt;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }
}
