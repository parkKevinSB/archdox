package com.archdox.cloud.reportai.application;

import com.archdox.cloud.reportai.domain.ReportPreflightReviewFinding;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewRun;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewFindingRepository;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewRunRepository;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.FindingSink;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReportPreflightAiReviewFindingSink implements FindingSink {
    private static final String SOURCE = "AI";

    private final ReportPreflightReviewRunRepository runRepository;
    private final ReportPreflightReviewFindingRepository findingRepository;
    private final ReportPhotoEvidenceStatusService photoEvidenceStatusService;

    public ReportPreflightAiReviewFindingSink(
            ReportPreflightReviewRunRepository runRepository,
            ReportPreflightReviewFindingRepository findingRepository,
            ReportPhotoEvidenceStatusService photoEvidenceStatusService
    ) {
        this.runRepository = runRepository;
        this.findingRepository = findingRepository;
        this.photoEvidenceStatusService = photoEvidenceStatusService;
    }

    @Override
    @Transactional
    public void accept(List<AiFinding> findings, AiHarnessRunContext ctx) {
        var run = runRepository.findByHarnessRunId(ctx.runId().value())
                .orElseThrow(() -> new IllegalStateException("Report preflight AI review run not found: " + ctx.runId().value()));
        findingRepository.deleteByReviewRunIdAndSource(run.id(), SOURCE);
        for (var finding : findings) {
            if (shouldSuppressBenignDailyLogPhotoPayloadFinding(run, finding)) {
                continue;
            }
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
        attributes.put("draftOnly", "true");
        attributes.put("approvalRequired", "true");
        attributes.put("reviewMode", ReportPreflightAiHarnessFlowService.REVIEW_MODE_SOURCE_BACKED_LEGAL_DRY_RUN);
        return attributes;
    }

    private boolean shouldSuppressBenignDailyLogPhotoPayloadFinding(
            ReportPreflightReviewRun run,
            AiFinding finding
    ) {
        if (!isBenignPhotosPayloadObservation(finding)) {
            return false;
        }
        var status = photoEvidenceStatusService.evaluate(run.officeId(), run.reportId());
        if (status.dailyLogReferencedPhotoIds().isEmpty()) {
            return false;
        }
        return status.missingDailyLogPhotoIds().isEmpty()
                && status.pendingDailyLogPhotoIds().isEmpty();
    }

    private boolean isBenignPhotosPayloadObservation(AiFinding finding) {
        var text = String.join(" ",
                        safe(finding.code()),
                        safe(finding.location()),
                        safe(finding.message()),
                        safe(finding.evidence()))
                .toUpperCase(Locale.ROOT);
        var mentionsPhotos = text.contains("PHOTOS") || text.contains("사진");
        if (!mentionsPhotos) {
            return false;
        }
        var mentionsPayloadShape = text.contains("PAYLOAD")
                || text.contains("페이로드")
                || text.contains("단계");
        var mentionsDailyLogResolvedEvidence = text.contains("DAILY_LOG")
                || text.contains("참조")
                || text.contains("정상")
                || text.contains("업로드");
        return mentionsPayloadShape && mentionsDailyLogResolvedEvidence;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
