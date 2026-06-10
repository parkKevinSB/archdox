package com.archdox.cloud.reportai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.domain.InspectionReportStep;
import com.archdox.cloud.inspection.domain.PayloadStorageMode;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.photo.domain.Photo;
import com.archdox.cloud.photo.domain.PhotoCaptureKind;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.domain.PhotoStorageKind;
import com.archdox.cloud.photo.domain.PhotoUploadTarget;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewFinding;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewRun;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewFindingRepository;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFindingSeverity;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ReportPreflightAiReviewFindingSinkTest {
    private final ReportPreflightReviewRunRepository runRepository = mock(ReportPreflightReviewRunRepository.class);
    private final ReportPreflightReviewFindingRepository findingRepository = mock(ReportPreflightReviewFindingRepository.class);
    private final InspectionReportStepRepository stepRepository = mock(InspectionReportStepRepository.class);
    private final PhotoRepository photoRepository = mock(PhotoRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReportPreflightAiReviewFindingSink sink = new ReportPreflightAiReviewFindingSink(
            runRepository,
            findingRepository,
            stepRepository,
            photoRepository,
            objectMapper);

    @Test
    void storesAiFindingsAsDraftsThatRequireApproval() {
        var run = new ReportPreflightReviewRun(10L, 20L, 3, 7L, OffsetDateTime.now());
        ReflectionTestUtils.setField(run, "id", 30L);
        when(runRepository.findByHarnessRunId("harness-run-1")).thenReturn(Optional.of(run));

        var ctx = new AiHarnessRunContext(
                new AiHarnessRunId("harness-run-1"),
                "archdox.report-preflight",
                new PromptVersion("archdox-report-preflight", "1.1.0"),
                Instant.parse("2026-06-10T00:00:00Z"));
        sink.accept(List.of(new AiFinding(
                "LEGAL_WORDING_REVIEW_REQUIRED",
                AiFindingSeverity.MEDIUM,
                "법령 근거와 감리 내용 연결을 사람이 확인해야 합니다.",
                "source-backed legal reference was supplied",
                "DAILY_LOG",
                Map.of("category", "LEGAL_RISK"))), ctx);

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
    void suppressesBenignPhotosPayloadFindingWhenDailyLogPhotoRefsAreUploaded() {
        var run = new ReportPreflightReviewRun(10L, 20L, 3, 7L, OffsetDateTime.now());
        ReflectionTestUtils.setField(run, "id", 30L);
        when(runRepository.findByHarnessRunId("harness-run-1")).thenReturn(Optional.of(run));
        when(stepRepository.findByReportIdAndStepCode(20L, "DAILY_LOG")).thenReturn(Optional.of(step(Map.of(
                "dailyItems", Map.of("groups", List.of(Map.of(
                        "entries", List.of(Map.of(
                                "supervisionContent", "확인했습니다.",
                                "photoIds", List.of(101L)
                        ))
                )))
        ))));
        when(photoRepository.findByOfficeIdAndReportIdAndStatusNotOrderByIdDesc(10L, 20L, PhotoStatus.DELETED))
                .thenReturn(List.of(photo(101L)));

        sink.accept(List.of(new AiFinding(
                "PHOTOS_PAYLOAD_EMPTY",
                AiFindingSeverity.MEDIUM,
                "PHOTOS 단계의 사진 첨부 페이로드가 비어 있으나, DAILY_LOG에서 참조하는 사진은 정상 업로드되어 있습니다.",
                "DAILY_LOG photo references are uploaded.",
                "PHOTOS step",
                Map.of("category", "COMPLETENESS"))), ctx());

        verify(findingRepository).deleteByReviewRunIdAndSource(30L, "AI");
        verify(findingRepository, never()).save(any());
    }

    @Test
    void keepsPhotosPayloadFindingWhenDailyLogPhotoRefsAreMissing() {
        var run = new ReportPreflightReviewRun(10L, 20L, 3, 7L, OffsetDateTime.now());
        ReflectionTestUtils.setField(run, "id", 30L);
        when(runRepository.findByHarnessRunId("harness-run-1")).thenReturn(Optional.of(run));
        when(stepRepository.findByReportIdAndStepCode(20L, "DAILY_LOG")).thenReturn(Optional.of(step(Map.of(
                "dailyItems", Map.of("groups", List.of(Map.of(
                        "entries", List.of(Map.of(
                                "supervisionContent", "확인했습니다.",
                                "photoIds", List.of(101L)
                        ))
                )))
        ))));
        when(photoRepository.findByOfficeIdAndReportIdAndStatusNotOrderByIdDesc(10L, 20L, PhotoStatus.DELETED))
                .thenReturn(List.of());

        sink.accept(List.of(new AiFinding(
                "PHOTOS_PAYLOAD_EMPTY",
                AiFindingSeverity.MEDIUM,
                "PHOTOS 단계의 사진 첨부 페이로드가 비어 있으나, DAILY_LOG에서 참조하는 사진은 정상 업로드되어 있습니다.",
                "DAILY_LOG photo references are uploaded.",
                "PHOTOS step",
                Map.of("category", "COMPLETENESS"))), ctx());

        verify(findingRepository).save(any(ReportPreflightReviewFinding.class));
    }

    private AiHarnessRunContext ctx() {
        return new AiHarnessRunContext(
                new AiHarnessRunId("harness-run-1"),
                "archdox.report-preflight",
                new PromptVersion("archdox-report-preflight", "1.1.0"),
                Instant.parse("2026-06-10T00:00:00Z"));
    }

    private InspectionReportStep step(Map<String, Object> payload) {
        return new InspectionReportStep(
                report(),
                "DAILY_LOG",
                PayloadStorageMode.CLOUD_ENCRYPTED,
                payload,
                7L,
                OffsetDateTime.now());
    }

    private InspectionReport report() {
        var report = new InspectionReport(
                10L,
                40L,
                50L,
                "R-001",
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "공사감리일지",
                60L,
                7L,
                OffsetDateTime.now());
        ReflectionTestUtils.setField(report, "id", 20L);
        return report;
    }

    private Photo photo(Long id) {
        var photo = new Photo(
                10L,
                40L,
                20L,
                "DAILY_LOG",
                null,
                PhotoCaptureKind.CAMERA,
                "image/jpeg",
                1024L,
                "hash-" + id,
                PhotoStorageKind.API_LOCAL,
                "photos/" + id + ".jpg",
                "photos/" + id + "-thumb.jpg",
                PhotoUploadTarget.API_LOCAL,
                7L,
                OffsetDateTime.now(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OffsetDateTime.now());
        ReflectionTestUtils.setField(photo, "id", id);
        photo.confirm(1024L, 100, 100, 7L, OffsetDateTime.now());
        return photo;
    }
}
