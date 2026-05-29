package com.archdox.cloud.documentai.domain;

import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunSnapshot;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "document_ai_review_runs")
public class DocumentAiReviewRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "document_job_id", nullable = false)
    private Long documentJobId;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "harness_run_id", nullable = false, unique = true)
    private String harnessRunId;

    @Column(name = "harness_id", nullable = false)
    private String harnessId;

    @Column(name = "prompt_id", nullable = false)
    private String promptId;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiHarnessRunStatus status;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "current_call_id")
    private String currentCallId;

    @Column(name = "terminal_reason")
    private String terminalReason;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    protected DocumentAiReviewRun() {
    }

    public DocumentAiReviewRun(
            Long officeId,
            Long documentJobId,
            Long reportId,
            String harnessRunId,
            String harnessId,
            PromptVersion promptVersion,
            AiHarnessRunStatus status,
            Long requestedBy,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.documentJobId = documentJobId;
        this.reportId = reportId;
        this.harnessRunId = harnessRunId;
        this.harnessId = harnessId;
        this.promptId = promptVersion.id();
        this.promptVersion = promptVersion.version();
        this.status = status;
        this.requestedBy = requestedBy;
        this.requestedAt = now;
        this.updatedAt = now;
    }

    public boolean markFromSnapshot(AiHarnessRunSnapshot snapshot, OffsetDateTime now) {
        boolean wasTerminal = completedAt != null;
        this.status = snapshot.status();
        this.attempt = snapshot.attempt();
        this.currentCallId = snapshot.currentCallId().orElse(null);
        this.terminalReason = snapshot.terminalReason().orElse(null);
        this.updatedAt = now;
        if (isTerminal(snapshot.status()) && this.completedAt == null) {
            this.completedAt = OffsetDateTime.ofInstant(snapshot.capturedAt(), ZoneOffset.UTC);
        }
        return !wasTerminal && completedAt != null;
    }

    public boolean markSucceededWithoutHarness(String terminalReason, OffsetDateTime now) {
        boolean wasTerminal = completedAt != null;
        this.status = AiHarnessRunStatus.SUCCEEDED;
        this.currentCallId = null;
        this.terminalReason = terminalReason;
        this.updatedAt = now;
        if (this.completedAt == null) {
            this.completedAt = now;
        }
        return !wasTerminal;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public Long documentJobId() {
        return documentJobId;
    }

    public Long reportId() {
        return reportId;
    }

    public String harnessRunId() {
        return harnessRunId;
    }

    public String harnessId() {
        return harnessId;
    }

    public PromptVersion promptVersion() {
        return new PromptVersion(promptId, promptVersion);
    }

    public AiHarnessRunStatus status() {
        return status;
    }

    public int attempt() {
        return attempt;
    }

    public String currentCallId() {
        return currentCallId;
    }

    public String terminalReason() {
        return terminalReason;
    }

    public Long requestedBy() {
        return requestedBy;
    }

    public OffsetDateTime requestedAt() {
        return requestedAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    public OffsetDateTime completedAt() {
        return completedAt;
    }

    private static boolean isTerminal(AiHarnessRunStatus status) {
        return status == AiHarnessRunStatus.SUCCEEDED
                || status == AiHarnessRunStatus.FAILED
                || status == AiHarnessRunStatus.CANCELLED;
    }
}
