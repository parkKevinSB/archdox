package com.archdox.cloud.documentai.application;

import com.archdox.cloud.documentai.domain.DocumentAiReviewFinding;
import com.archdox.cloud.documentai.infra.DocumentAiReviewFindingRepository;
import com.archdox.cloud.documentai.infra.DocumentAiReviewRunRepository;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.FindingSink;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DocumentAiReviewFindingSink implements FindingSink {
    private final DocumentAiReviewRunRepository runRepository;
    private final DocumentAiReviewFindingRepository findingRepository;

    public DocumentAiReviewFindingSink(
            DocumentAiReviewRunRepository runRepository,
            DocumentAiReviewFindingRepository findingRepository
    ) {
        this.runRepository = runRepository;
        this.findingRepository = findingRepository;
    }

    @Override
    @Transactional
    public void accept(List<AiFinding> findings, AiHarnessRunContext ctx) {
        var run = runRepository.findByHarnessRunId(ctx.runId().value())
                .orElseThrow(() -> new IllegalStateException("Document AI review run not found: " + ctx.runId().value()));
        findingRepository.deleteByReviewRunId(run.id());
        for (var finding : findings) {
            findingRepository.save(new DocumentAiReviewFinding(
                    run.officeId(),
                    run.id(),
                    run.documentJobId(),
                    run.reportId(),
                    finding.code(),
                    finding.severity().name(),
                    finding.location(),
                    finding.message(),
                    finding.evidence(),
                    finding.attributes(),
                    OffsetDateTime.now()));
        }
    }
}
