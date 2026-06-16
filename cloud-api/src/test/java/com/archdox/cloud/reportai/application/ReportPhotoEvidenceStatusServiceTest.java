package com.archdox.cloud.reportai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.domain.InspectionReportStep;
import com.archdox.cloud.inspection.domain.PayloadStorageMode;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.photo.domain.Photo;
import com.archdox.cloud.photo.domain.PhotoAssetStatus;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.domain.PhotoCaptureKind;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.domain.PhotoStorageKind;
import com.archdox.cloud.photo.domain.PhotoUploadTarget;
import com.archdox.cloud.photo.infra.PhotoAssetRepository;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReportPhotoEvidenceStatusServiceTest {
    private final InspectionReportStepRepository stepRepository = mock(InspectionReportStepRepository.class);
    private final PhotoRepository photoRepository = mock(PhotoRepository.class);
    private final PhotoAssetRepository photoAssetRepository = mock(PhotoAssetRepository.class);
    private final ReportPhotoEvidenceStatusService service = new ReportPhotoEvidenceStatusService(
            stepRepository,
            photoRepository,
            photoAssetRepository,
            new ObjectMapper());

    @Test
    void resolvesDailyLogLinkedAndUnlinkedPhotosFromReportPhotos() {
        var report = report();
        var linkedPhoto = uploadedPhoto(101L);
        var unlinkedPhoto = uploadedPhoto(202L);
        when(stepRepository.findByReportIdAndStepCode(100L, "DAILY_LOG"))
                .thenReturn(Optional.of(step(report, dailyLogPayload(List.of(101L)))));
        when(stepRepository.findByReportIdAndStepCode(100L, "PHOTOS")).thenReturn(Optional.empty());
        when(photoRepository.findByOfficeIdAndReportIdAndStatusNotOrderByIdDesc(10L, 100L, PhotoStatus.DELETED))
                .thenReturn(List.of(linkedPhoto, unlinkedPhoto));
        when(photoAssetRepository.findPhotoIdsByAssetTypeAndStatus(
                List.of(101L, 202L),
                PhotoAssetType.WORKING,
                PhotoAssetStatus.UPLOADED))
                .thenReturn(List.of(101L, 202L));

        var status = service.evaluate(report);

        assertThat(status.photosStepPayloadEmpty()).isTrue();
        assertThat(status.dailyLogReferencedPhotoIds()).containsExactly(101L);
        assertThat(status.linkedDailyLogPhotoIds()).containsExactly(101L);
        assertThat(status.unlinkedPhotoIds()).containsExactly(202L);
        assertThat(status.missingDailyLogPhotoIds()).isEmpty();
        assertThat(status.allDailyLogPhotoRefsResolved()).isTrue();
        assertThat(status.hasGenerationBlockingPhotoIssue()).isFalse();
    }

    @Test
    void marksMissingDailyLogPhotoReferenceAsBlockingStatus() {
        var report = report();
        when(stepRepository.findByReportIdAndStepCode(100L, "DAILY_LOG"))
                .thenReturn(Optional.of(step(report, dailyLogPayload(List.of(101L)))));
        when(stepRepository.findByReportIdAndStepCode(100L, "PHOTOS")).thenReturn(Optional.empty());
        when(photoRepository.findByOfficeIdAndReportIdAndStatusNotOrderByIdDesc(10L, 100L, PhotoStatus.DELETED))
                .thenReturn(List.of());

        var status = service.evaluate(report);

        assertThat(status.missingDailyLogPhotoIds()).containsExactly(101L);
        assertThat(status.allDailyLogPhotoRefsResolved()).isFalse();
        assertThat(status.hasGenerationBlockingPhotoIssue()).isTrue();
    }

    private InspectionReport report() {
        var report = new InspectionReport(
                10L,
                20L,
                30L,
                "R-001",
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "공사감리일지",
                40L,
                50L,
                OffsetDateTime.now());
        ReflectionTestUtils.setField(report, "id", 100L);
        return report;
    }

    private InspectionReportStep step(InspectionReport report, Map<String, Object> payload) {
        return new InspectionReportStep(
                report,
                "DAILY_LOG",
                PayloadStorageMode.CLOUD_ENCRYPTED,
                payload,
                50L,
                OffsetDateTime.now());
    }

    private Map<String, Object> dailyLogPayload(List<Long> photoIds) {
        return Map.of(
                "dailyItems", Map.of("groups", List.of(Map.of(
                        "floor", "1F",
                        "tradeCode", "REINFORCED_CONCRETE",
                        "processCode", "REBAR_ASSEMBLY",
                        "entries", List.of(Map.of(
                                "inspectionItemCode", "RC_REBAR_CONFIRMATION",
                                "inspectionItemName", "철근배근의 확인사항",
                                "checklistRows", List.of(Map.of(
                                        "code", "RC_REBAR_COUNT_DIAMETER_PITCH",
                                        "label", "개수, 철근지름, 피치 확인",
                                        "result", "COMPLIANT",
                                        "photoIds", photoIds)),
                                "supervisionContent", "철근 배근 상태를 확인했습니다."))))));
    }

    private Photo uploadedPhoto(Long id) {
        var photo = new Photo(
                10L,
                20L,
                100L,
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
                50L,
                OffsetDateTime.now(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OffsetDateTime.now());
        ReflectionTestUtils.setField(photo, "id", id);
        photo.confirm(1024L, 100, 100, 50L, OffsetDateTime.now());
        return photo;
    }

}
