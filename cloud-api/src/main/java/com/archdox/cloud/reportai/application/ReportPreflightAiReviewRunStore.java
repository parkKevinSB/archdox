package com.archdox.cloud.reportai.application;

import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewRunRepository;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunId;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunSnapshot;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStatus;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStore;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReportPreflightAiReviewRunStore implements AiHarnessRunStore {
    private final ReportPreflightReviewRunRepository repository;
    private final OperationEventService operationEventService;

    public ReportPreflightAiReviewRunStore(
            ReportPreflightReviewRunRepository repository,
            OperationEventService operationEventService
    ) {
        this.repository = repository;
        this.operationEventService = operationEventService;
    }

    @Override
    @Transactional
    public void save(AiHarnessRunSnapshot snapshot) {
        var run = repository.findByHarnessRunId(snapshot.runId().value())
                .orElseThrow(() -> new IllegalStateException("Report preflight AI review run not found: " + snapshot.runId().value()));
        boolean becameTerminal = run.markHarnessSnapshot(snapshot, OffsetDateTime.now());
        if (becameTerminal) {
            operationEventService.record(
                    run.officeId(),
                    snapshot.status() == AiHarnessRunStatus.SUCCEEDED ? OperationEventSeverity.INFO : OperationEventSeverity.WARN,
                    "REPORT_PREFLIGHT_AI_REVIEW_" + snapshot.status().name(),
                    "report-preflight-review",
                    "report:" + run.reportId() + ":preflight-run:" + run.id(),
                    "REPORT_PREFLIGHT_REVIEW_RUN",
                    run.id(),
                    run.requestedBy(),
                    null,
                    "Report preflight AI review harness finished with status " + snapshot.status().name() + ".",
                    Map.of(
                            "reportId", run.reportId(),
                            "reviewRunId", run.id(),
                            "harnessRunId", run.harnessRunId()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AiHarnessRunSnapshot> find(AiHarnessRunId runId) {
        return repository.findByHarnessRunId(runId.value())
                .filter(run -> run.promptVersion() != null && run.harnessStatus() != null)
                .map(run -> new AiHarnessRunSnapshot(
                        new AiHarnessRunId(run.harnessRunId()),
                        run.harnessId(),
                        run.promptVersion(),
                        AiHarnessRunStatus.valueOf(run.harnessStatus()),
                        run.harnessAttempt(),
                        run.requestedAt().toInstant(),
                        run.updatedAt().toInstant(),
                        Optional.empty(),
                        Optional.ofNullable(run.harnessCurrentCallId()),
                        Optional.empty(),
                        Optional.ofNullable(run.harnessTerminalReason())));
    }
}
