package com.archdox.cloud.documentai.application;

import com.archdox.cloud.documentai.infra.DocumentAiReviewRunRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunId;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunSnapshot;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStore;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DocumentAiReviewRunStore implements AiHarnessRunStore {
    private final DocumentAiReviewRunRepository repository;
    private final OperationEventService operationEventService;

    public DocumentAiReviewRunStore(
            DocumentAiReviewRunRepository repository,
            OperationEventService operationEventService
    ) {
        this.repository = repository;
        this.operationEventService = operationEventService;
    }

    @Override
    @Transactional
    public void save(AiHarnessRunSnapshot snapshot) {
        var run = repository.findByHarnessRunId(snapshot.runId().value())
                .orElseThrow(() -> new IllegalStateException("Document AI review run not found: " + snapshot.runId().value()));
        boolean becameTerminal = run.markFromSnapshot(snapshot, OffsetDateTime.now());
        if (becameTerminal) {
            operationEventService.record(
                    run.officeId(),
                    snapshot.status().name().equals("SUCCEEDED") ? OperationEventSeverity.INFO : OperationEventSeverity.WARN,
                    "DOCUMENT_AI_REVIEW_" + snapshot.status().name(),
                    "document-ai-review",
                    "document-ai-review-run:" + run.id(),
                    "DOCUMENT_AI_REVIEW_RUN",
                    run.id(),
                    run.requestedBy(),
                    null,
                    "Document AI review run finished with status " + snapshot.status().name() + ".",
                    Map.of(
                            "documentJobId", run.documentJobId(),
                            "reportId", run.reportId(),
                            "harnessRunId", run.harnessRunId()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AiHarnessRunSnapshot> find(AiHarnessRunId runId) {
        return repository.findByHarnessRunId(runId.value()).map(run -> new AiHarnessRunSnapshot(
                new AiHarnessRunId(run.harnessRunId()),
                run.harnessId(),
                run.promptVersion(),
                run.status(),
                run.attempt(),
                run.requestedAt().toInstant(),
                run.updatedAt().toInstant(),
                Optional.empty(),
                Optional.ofNullable(run.currentCallId()),
                Optional.empty(),
                Optional.ofNullable(run.terminalReason())));
    }

}
