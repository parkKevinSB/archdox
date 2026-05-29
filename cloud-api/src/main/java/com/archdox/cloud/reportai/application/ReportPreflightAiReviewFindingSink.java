package com.archdox.cloud.reportai.application;

import com.archdox.cloud.reportai.domain.ReportPreflightReviewFinding;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewFindingRepository;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewRunRepository;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.FindingSink;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReportPreflightAiReviewFindingSink implements FindingSink {
    private static final String SOURCE = "AI";

    private final ReportPreflightReviewRunRepository runRepository;
    private final ReportPreflightReviewFindingRepository findingRepository;

    public ReportPreflightAiReviewFindingSink(
            ReportPreflightReviewRunRepository runRepository,
            ReportPreflightReviewFindingRepository findingRepository
    ) {
        this.runRepository = runRepository;
        this.findingRepository = findingRepository;
    }

    @Override
    @Transactional
    public void accept(List<AiFinding> findings, AiHarnessRunContext ctx) {
        var run = runRepository.findByHarnessRunId(ctx.runId().value())
                .orElseThrow(() -> new IllegalStateException("Report preflight AI review run not found: " + ctx.runId().value()));
        findingRepository.deleteByReviewRunIdAndSource(run.id(), SOURCE);
        for (var finding : findings) {
            findingRepository.save(new ReportPreflightReviewFinding(
                    run.officeId(),
                    run.id(),
                    run.reportId(),
                    SOURCE,
                    finding.code(),
                    finding.severity().name(),
                    finding.location(),
                    finding.message(),
                    finding.evidence(),
                    attributes(finding),
                    OffsetDateTime.now()));
        }
    }

    private LinkedHashMap<String, String> attributes(AiFinding finding) {
        var attributes = new LinkedHashMap<>(finding.attributes());
        attributes.put("source", SOURCE);
        return attributes;
    }
}
