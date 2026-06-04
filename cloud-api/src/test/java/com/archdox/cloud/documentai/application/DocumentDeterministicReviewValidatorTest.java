package com.archdox.cloud.documentai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.archdox.cloud.document.domain.DocumentArtifact;
import com.archdox.cloud.document.domain.DocumentArtifactStorageKind;
import com.archdox.cloud.document.domain.DocumentArtifactType;
import com.archdox.cloud.document.domain.DocumentJob;
import com.archdox.cloud.document.domain.DocumentWorkerType;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.document.OutputFormat;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DocumentDeterministicReviewValidatorTest {
    private final DocumentDeterministicReviewValidator validator = new DocumentDeterministicReviewValidator();

    @Test
    void missingExpectedArtifactBlocksAiReview() {
        var job = job(OutputFormat.DOCX_AND_PDF);
        var report = report();
        var result = validator.validate(job, report, List.of(artifact(DocumentArtifactType.DOCX, 100)));

        assertTrue(result.blocksAiReview());
        assertTrue(result.findings().stream()
                .anyMatch(finding -> "DOCUMENT_ARTIFACT_TYPE_MISSING".equals(finding.code())));
    }

    @Test
    void missingPhotoWorkingAssetBlocksAiReview() {
        var job = jobWithPhotoMissingWorkingAsset();
        var report = report();
        var result = validator.validate(job, report, List.of(artifact(DocumentArtifactType.DOCX, 100)));

        assertTrue(result.blocksAiReview());
        assertTrue(result.findings().stream()
                .anyMatch(finding -> "PHOTO_WORKING_ASSET_MISSING".equals(finding.code())));
    }

    @Test
    void completeSnapshotAndArtifactPassesWithoutAiBlockingFindings() {
        var job = job(OutputFormat.DOCX);
        var report = report();
        var result = validator.validate(job, report, List.of(artifact(DocumentArtifactType.DOCX, 100)));

        assertFalse(result.blocksAiReview());
        assertEquals(0, result.findings().size());
    }

    private DocumentJob job(OutputFormat outputFormat) {
        return new DocumentJob(
                10L,
                100L,
                20L,
                1,
                30L,
                40L,
                DocumentWorkerType.ARCHDOX_AGENT,
                outputFormat,
                validSnapshot(List.of()),
                OffsetDateTime.now());
    }

    private DocumentJob jobWithPhotoMissingWorkingAsset() {
        return new DocumentJob(
                10L,
                100L,
                20L,
                1,
                30L,
                40L,
                DocumentWorkerType.ARCHDOX_AGENT,
                OutputFormat.DOCX,
                validSnapshot(List.of(Map.of(
                        "photoId", 77L,
                        "workingStorageRef", "",
                        "thumbnailStorageRef", "photos/77/thumb.webp"))),
                OffsetDateTime.now());
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

    private DocumentArtifact artifact(DocumentArtifactType type, long bytes) {
        return new DocumentArtifact(
                10L,
                200L,
                100L,
                type,
                DocumentArtifactStorageKind.ARCHDOX_AGENT,
                "documents/jobs/200/result." + type.name().toLowerCase(),
                "result." + type.name().toLowerCase(),
                "application/octet-stream",
                bytes,
                "hash",
                OffsetDateTime.now());
    }

    private Map<String, Object> validSnapshot(List<Map<String, Object>> photos) {
        return Map.of(
                "report", Map.of("id", 100L, "reportType", "CONSTRUCTION_DAILY_SUPERVISION_LOG"),
                "configuration", Map.of("reportType", "CONSTRUCTION_DAILY_SUPERVISION_LOG"),
                "templateFields", Map.of("projectName", "테스트 현장"),
                "layoutSections", List.of(Map.of("title", "기본 정보")),
                "photos", photos);
    }
}
