package com.archdox.cloud.documentai.application;

import com.archdox.cloud.document.domain.DocumentArtifact;
import com.archdox.cloud.document.domain.DocumentArtifactType;
import com.archdox.cloud.document.domain.DocumentJob;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.document.OutputFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DocumentDeterministicReviewValidator {
    public DeterministicDocumentReviewResult validate(
            DocumentJob job,
            InspectionReport report,
            List<DocumentArtifact> artifacts
    ) {
        var findings = new ArrayList<DeterministicDocumentReviewFinding>();
        validateSnapshot(job, findings);
        validateReportConsistency(job, report, findings);
        validateArtifacts(job, artifacts, findings);
        validatePhotoAssets(job, findings);
        return new DeterministicDocumentReviewResult(findings);
    }

    private void validateSnapshot(
            DocumentJob job,
            List<DeterministicDocumentReviewFinding> findings
    ) {
        var snapshot = job.inputSnapshotJson();
        if (snapshot == null || snapshot.isEmpty()) {
            findings.add(finding(
                    "SNAPSHOT_EMPTY",
                    "CRITICAL",
                    "documentJob.inputSnapshotJson",
                    "문서 검토에 필요한 생성 스냅샷이 비어 있습니다.",
                    "inputSnapshotJson is empty",
                    Map.of("source", "DETERMINISTIC", "blocksAiReview", "true")));
            return;
        }
        requireSnapshotKey(snapshot, "report", findings);
        requireSnapshotKey(snapshot, "configuration", findings);
        requireSnapshotKey(snapshot, "templateFields", findings);
        requireSnapshotKey(snapshot, "layoutSections", findings);
    }

    private void requireSnapshotKey(
            Map<String, Object> snapshot,
            String key,
            List<DeterministicDocumentReviewFinding> findings
    ) {
        if (!snapshot.containsKey(key) || isEmptyValue(snapshot.get(key))) {
            findings.add(finding(
                    "SNAPSHOT_SECTION_MISSING",
                    "HIGH",
                    "snapshot." + key,
                    "문서 생성 스냅샷에 필수 섹션이 없습니다: " + key,
                    "Missing snapshot key: " + key,
                    Map.of("source", "DETERMINISTIC", "blocksAiReview", "true", "snapshotKey", key)));
        }
    }

    private void validateReportConsistency(
            DocumentJob job,
            InspectionReport report,
            List<DeterministicDocumentReviewFinding> findings
    ) {
        if (!job.officeId().equals(report.officeId())) {
            findings.add(finding(
                    "REPORT_OFFICE_MISMATCH",
                    "CRITICAL",
                    "documentJob.officeId",
                    "문서 작업의 사무소와 리포트의 사무소가 일치하지 않습니다.",
                    "documentJob.officeId != report.officeId",
                    Map.of("source", "DETERMINISTIC", "blocksAiReview", "true")));
        }
        if (!job.reportId().equals(report.id())) {
            findings.add(finding(
                    "REPORT_ID_MISMATCH",
                    "CRITICAL",
                    "documentJob.reportId",
                    "문서 작업의 리포트 ID와 실제 리포트 ID가 일치하지 않습니다.",
                    "documentJob.reportId != report.id",
                    Map.of("source", "DETERMINISTIC", "blocksAiReview", "true")));
        }
    }

    private void validateArtifacts(
            DocumentJob job,
            List<DocumentArtifact> artifacts,
            List<DeterministicDocumentReviewFinding> findings
    ) {
        if (artifacts == null || artifacts.isEmpty()) {
            findings.add(finding(
                    "DOCUMENT_ARTIFACTS_MISSING",
                    "CRITICAL",
                    "documentArtifacts",
                    "생성된 문서 파일 정보가 없습니다.",
                    "No document artifacts were found for the generated document job",
                    Map.of("source", "DETERMINISTIC", "blocksAiReview", "true")));
            return;
        }
        var actualTypes = EnumSet.noneOf(DocumentArtifactType.class);
        for (DocumentArtifact artifact : artifacts) {
            actualTypes.add(artifact.artifactType());
            validateArtifactMetadata(artifact, findings);
        }
        for (DocumentArtifactType expected : expectedArtifactTypes(job.outputFormat())) {
            if (!actualTypes.contains(expected)) {
                findings.add(finding(
                        "DOCUMENT_ARTIFACT_TYPE_MISSING",
                        "CRITICAL",
                        "documentArtifacts." + expected.name(),
                        "요청한 출력 형식에 필요한 문서 파일이 없습니다: " + expected.name(),
                        "Missing expected artifact type: " + expected.name(),
                        Map.of(
                                "source", "DETERMINISTIC",
                                "blocksAiReview", "true",
                                "expectedArtifactType", expected.name(),
                                "outputFormat", job.outputFormat().name())));
            }
        }
    }

    private void validateArtifactMetadata(
            DocumentArtifact artifact,
            List<DeterministicDocumentReviewFinding> findings
    ) {
        if (artifact.bytes() <= 0) {
            findings.add(finding(
                    "DOCUMENT_ARTIFACT_EMPTY",
                    "CRITICAL",
                    "documentArtifacts." + artifact.artifactType().name() + ".bytes",
                    "생성된 문서 파일의 크기가 0입니다.",
                    "artifact bytes <= 0",
                    Map.of("source", "DETERMINISTIC", "blocksAiReview", "true")));
        }
        if (isBlank(artifact.storageRef())) {
            findings.add(finding(
                    "DOCUMENT_ARTIFACT_STORAGE_REF_MISSING",
                    "CRITICAL",
                    "documentArtifacts." + artifact.artifactType().name() + ".storageRef",
                    "생성된 문서 파일의 저장 위치가 없습니다.",
                    "artifact storageRef is blank",
                    Map.of("source", "DETERMINISTIC", "blocksAiReview", "true")));
        }
        if (isBlank(artifact.fileName()) || isBlank(artifact.mimeType()) || isBlank(artifact.hashSha256())) {
            findings.add(finding(
                    "DOCUMENT_ARTIFACT_METADATA_INCOMPLETE",
                    "HIGH",
                    "documentArtifacts." + artifact.artifactType().name(),
                    "생성된 문서 파일의 메타데이터가 부족합니다.",
                    "artifact fileName, mimeType, or hashSha256 is blank",
                    Map.of("source", "DETERMINISTIC", "blocksAiReview", "true")));
        }
    }

    private void validatePhotoAssets(
            DocumentJob job,
            List<DeterministicDocumentReviewFinding> findings
    ) {
        var snapshot = job.inputSnapshotJson();
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        for (Map<String, Object> photo : listValue(snapshot.get("photos"))) {
            var photoId = stringValue(photo.get("photoId"));
            if (isBlank(stringValue(photo.get("workingStorageRef")))) {
                findings.add(finding(
                        "PHOTO_WORKING_ASSET_MISSING",
                        "HIGH",
                        "snapshot.photos[" + photoId + "].workingStorageRef",
                        "문서에 사용될 작업본 사진이 준비되지 않았습니다.",
                        "photo workingStorageRef is blank",
                        Map.of(
                                "source", "DETERMINISTIC",
                                "blocksAiReview", "true",
                                "photoId", photoId)));
            }
        }
    }

    private static Set<DocumentArtifactType> expectedArtifactTypes(OutputFormat outputFormat) {
        return switch (outputFormat) {
            case DOCX -> EnumSet.of(DocumentArtifactType.DOCX);
            case HTML -> EnumSet.of(DocumentArtifactType.HTML);
            case PDF -> EnumSet.of(DocumentArtifactType.PDF);
            case DOCX_AND_PDF -> EnumSet.of(DocumentArtifactType.DOCX, DocumentArtifactType.PDF);
            case HTML_AND_PDF -> EnumSet.of(DocumentArtifactType.HTML, DocumentArtifactType.PDF);
            case HWP -> EnumSet.of(DocumentArtifactType.HWP);
            case HWPX -> EnumSet.of(DocumentArtifactType.HWPX);
        };
    }

    private static DeterministicDocumentReviewFinding finding(
            String code,
            String severity,
            String location,
            String message,
            String evidence,
            Map<String, String> attributes
    ) {
        return new DeterministicDocumentReviewFinding(code, severity, location, message, evidence, attributes);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listValue(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private static boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        return false;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
