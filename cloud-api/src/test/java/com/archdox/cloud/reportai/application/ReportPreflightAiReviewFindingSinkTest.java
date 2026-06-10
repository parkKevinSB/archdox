package com.archdox.cloud.reportai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.reportai.domain.ReportPreflightReviewFinding;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewRun;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewFindingRepository;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewRunRepository;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFindingSeverity;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ReportPreflightAiReviewFindingSinkTest {
    private final ReportPreflightReviewRunRepository runRepository = mock(ReportPreflightReviewRunRepository.class);
    private final ReportPreflightReviewFindingRepository findingRepository = mock(ReportPreflightReviewFindingRepository.class);
    private final ReportPhotoEvidenceStatusService photoEvidenceStatusService = mock(ReportPhotoEvidenceStatusService.class);
    private final ReportPreflightAiReviewFindingSink sink = new ReportPreflightAiReviewFindingSink(
            runRepository,
            findingRepository,
            photoEvidenceStatusService);

    @Test
    void storesAiFindingsAsDraftsThatRequireApproval() {
        var run = run();
        when(runRepository.findByHarnessRunId("harness-run-1")).thenReturn(Optional.of(run));

        sink.accept(List.of(new AiFinding(
                "LEGAL_WORDING_REVIEW_REQUIRED",
                AiFindingSeverity.MEDIUM,
                "법령 근거와 감리 내용 연결을 사람이 확인해야 합니다.",
                "source-backed legal reference was supplied",
                "DAILY_LOG",
                Map.of("category", "LEGAL_RISK"))), ctx());

        var captor = ArgumentCaptor.forClass(ReportPreflightReviewFinding.class);
        verify(findingRepository).deleteByReviewRunIdAndSource(30L, "AI");
        verify(findingRepository).save(captor.capture());
        assertThat(captor.getValue().source()).isEqualTo("AI");
        assertThat(captor.getValue().severity()).isEqualTo("MEDIUM");
        assertThat(captor.getValue().attributesJson())
                .containsEntry("draftOnly", "true")
                .containsEntry("approvalRequired", "true")
                .containsEntry("reviewMode", ReportPreflightAiHarnessFlowService.REVIEW_MODE_SOURCE_BACKED_LEGAL_DRY_RUN);
    }

    @Test
    void suppressesBenignPhotosPayloadFindingWhenDailyLogPhotoRefsAreResolved() {
        var run = run();
        when(runRepository.findByHarnessRunId("harness-run-1")).thenReturn(Optional.of(run));
        when(photoEvidenceStatusService.evaluate(10L, 20L))
                .thenReturn(photoEvidenceStatus(Set.of(101L), Set.of(101L), Set.of()));

        sink.accept(List.of(photosPayloadFinding()), ctx());

        verify(findingRepository).deleteByReviewRunIdAndSource(30L, "AI");
        verify(findingRepository, never()).save(any());
    }

    @Test
    void keepsPhotosPayloadFindingWhenDailyLogPhotoRefsAreMissing() {
        var run = run();
        when(runRepository.findByHarnessRunId("harness-run-1")).thenReturn(Optional.of(run));
        when(photoEvidenceStatusService.evaluate(10L, 20L))
                .thenReturn(photoEvidenceStatus(Set.of(101L), Set.of(), Set.of(101L)));

        sink.accept(List.of(photosPayloadFinding()), ctx());

        verify(findingRepository).save(any(ReportPreflightReviewFinding.class));
    }

    private ReportPreflightReviewRun run() {
        var run = new ReportPreflightReviewRun(10L, 20L, 3, 7L, OffsetDateTime.now());
        ReflectionTestUtils.setField(run, "id", 30L);
        return run;
    }

    private AiHarnessRunContext ctx() {
        return new AiHarnessRunContext(
                new AiHarnessRunId("harness-run-1"),
                "archdox.report-preflight",
                new PromptVersion("archdox-report-preflight", "1.1.0"),
                Instant.parse("2026-06-10T00:00:00Z"));
    }

    private AiFinding photosPayloadFinding() {
        return new AiFinding(
                "PHOTOS_PAYLOAD_EMPTY",
                AiFindingSeverity.MEDIUM,
                "PHOTOS 단계의 사진 첨부 페이로드가 비어 있으나, DAILY_LOG에서 참조하는 사진은 정상 업로드되어 있습니다.",
                "DAILY_LOG photo references are uploaded.",
                "PHOTOS step",
                Map.of("category", "COMPLETENESS"));
    }

    private ReportPhotoEvidenceStatus photoEvidenceStatus(
            Set<Long> referencedIds,
            Set<Long> activeIds,
            Set<Long> missingIds
    ) {
        return new ReportPhotoEvidenceStatus(
                true,
                activeIds.size(),
                activeIds.size(),
                activeIds,
                activeIds,
                activeIds,
                referencedIds,
                activeIds,
                missingIds,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of());
    }
}
