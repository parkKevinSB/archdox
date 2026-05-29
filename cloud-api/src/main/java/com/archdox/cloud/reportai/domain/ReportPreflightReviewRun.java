package com.archdox.cloud.reportai.domain;

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

@Entity
@Table(name = "report_preflight_review_runs")
public class ReportPreflightReviewRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "report_revision", nullable = false)
    private int reportRevision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportPreflightReviewStatus status;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "terminal_reason")
    private String terminalReason;

    @Column(name = "harness_run_id")
    private String harnessRunId;

    @Column(name = "harness_id")
    private String harnessId;

    @Column(name = "prompt_id")
    private String promptId;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "harness_status")
    private String harnessStatus;

    @Column(name = "harness_attempt", nullable = false)
    private int harnessAttempt;

    @Column(name = "harness_current_call_id")
    private String harnessCurrentCallId;

    @Column(name = "harness_terminal_reason")
    private String harnessTerminalReason;

    @Column(name = "ai_provider_code")
    private String aiProviderCode;

    @Column(name = "ai_model_id")
    private String aiModelId;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    protected ReportPreflightReviewRun() {
    }

    public ReportPreflightReviewRun(
            Long officeId,
            Long reportId,
            int reportRevision,
            Long requestedBy,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.reportId = reportId;
        this.reportRevision = reportRevision;
        this.status = ReportPreflightReviewStatus.REQUESTED;
        this.requestedBy = requestedBy;
        this.requestedAt = now;
        this.updatedAt = now;
    }

    public void markRunning(OffsetDateTime now) {
        this.status = ReportPreflightReviewStatus.RUNNING;
        this.updatedAt = now;
        this.terminalReason = null;
        this.completedAt = null;
    }

    public void markPassed(String terminalReason, OffsetDateTime now) {
        markTerminal(ReportPreflightReviewStatus.PASSED, terminalReason, now);
    }

    public void markNeedsAttention(String terminalReason, OffsetDateTime now) {
        markTerminal(ReportPreflightReviewStatus.NEEDS_ATTENTION, terminalReason, now);
    }

    public void markFailed(String terminalReason, OffsetDateTime now) {
        markTerminal(ReportPreflightReviewStatus.FAILED, terminalReason, now);
    }

    public void attachHarness(
            String harnessRunId,
            String harnessId,
            PromptVersion promptVersion,
            String aiProviderCode,
            String aiModelId,
            OffsetDateTime now
    ) {
        this.harnessRunId = harnessRunId;
        this.harnessId = harnessId;
        this.promptId = promptVersion.id();
        this.promptVersion = promptVersion.version();
        this.harnessStatus = AiHarnessRunStatus.QUEUED.name();
        this.harnessAttempt = 0;
        this.harnessCurrentCallId = null;
        this.harnessTerminalReason = null;
        this.aiProviderCode = aiProviderCode;
        this.aiModelId = aiModelId;
        this.updatedAt = now;
    }

    public boolean markHarnessSnapshot(AiHarnessRunSnapshot snapshot, OffsetDateTime now) {
        var previousTerminal = harnessTerminal();
        this.harnessStatus = snapshot.status().name();
        this.harnessAttempt = snapshot.attempt();
        this.harnessCurrentCallId = snapshot.currentCallId().orElse(null);
        this.harnessTerminalReason = snapshot.terminalReason().orElse(null);
        this.updatedAt = now;
        if (snapshot.status() == AiHarnessRunStatus.FAILED) {
            markFailed(reasonOrDefault(this.harnessTerminalReason, "AI_PREFLIGHT_HARNESS_FAILED"), now);
        }
        if (snapshot.status() == AiHarnessRunStatus.CANCELLED) {
            markFailed(reasonOrDefault(this.harnessTerminalReason, "AI_PREFLIGHT_HARNESS_CANCELLED"), now);
        }
        return !previousTerminal && harnessTerminal();
    }

    private void markTerminal(ReportPreflightReviewStatus status, String terminalReason, OffsetDateTime now) {
        this.status = status;
        this.terminalReason = terminalReason;
        this.updatedAt = now;
        this.completedAt = now;
    }

    public boolean terminal() {
        return status == ReportPreflightReviewStatus.PASSED
                || status == ReportPreflightReviewStatus.NEEDS_ATTENTION
                || status == ReportPreflightReviewStatus.FAILED;
    }

    public boolean hasHarness() {
        return harnessRunId != null && !harnessRunId.isBlank();
    }

    public boolean harnessTerminal() {
        if (harnessStatus == null || harnessStatus.isBlank()) {
            return false;
        }
        return AiHarnessRunStatus.SUCCEEDED.name().equals(harnessStatus)
                || AiHarnessRunStatus.FAILED.name().equals(harnessStatus)
                || AiHarnessRunStatus.CANCELLED.name().equals(harnessStatus);
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public Long reportId() {
        return reportId;
    }

    public int reportRevision() {
        return reportRevision;
    }

    public ReportPreflightReviewStatus status() {
        return status;
    }

    public Long requestedBy() {
        return requestedBy;
    }

    public String terminalReason() {
        return terminalReason;
    }

    public String harnessRunId() {
        return harnessRunId;
    }

    public String harnessId() {
        return harnessId;
    }

    public PromptVersion promptVersion() {
        if (promptId == null || promptVersion == null) {
            return null;
        }
        return new PromptVersion(promptId, promptVersion);
    }

    public String harnessStatus() {
        return harnessStatus;
    }

    public int harnessAttempt() {
        return harnessAttempt;
    }

    public String harnessCurrentCallId() {
        return harnessCurrentCallId;
    }

    public String harnessTerminalReason() {
        return harnessTerminalReason;
    }

    public String aiProviderCode() {
        return aiProviderCode;
    }

    public String aiModelId() {
        return aiModelId;
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

    private static String reasonOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
