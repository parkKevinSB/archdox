package com.archdox.cloud.reportai.application;

import com.archdox.cloud.inspection.domain.InspectionReportStep;
import com.archdox.cloud.inspection.application.ReportSubmitValidationService;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.domain.InspectionReportStatus;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.inspectiontarget.infra.InspectionReportTargetRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ReportPreflightDeterministicValidator {
    private final ReportSubmitValidationService submitValidationService;
    private final InspectionReportTargetRepository targetRepository;
    private final InspectionReportStepRepository stepRepository;
    private final ReportPhotoEvidenceStatusService photoEvidenceStatusService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReportPreflightDeterministicValidator(
            ReportSubmitValidationService submitValidationService,
            InspectionReportTargetRepository targetRepository,
            InspectionReportStepRepository stepRepository,
            ReportPhotoEvidenceStatusService photoEvidenceStatusService
    ) {
        this.submitValidationService = submitValidationService;
        this.targetRepository = targetRepository;
        this.stepRepository = stepRepository;
        this.photoEvidenceStatusService = photoEvidenceStatusService;
    }

    public ReportPreflightValidationResult validate(InspectionReport report) {
        var findings = new ArrayList<ReportPreflightFinding>();
        validateReportState(report, findings);
        var submitValidation = submitValidationService.validate(report);
        for (var issue : submitValidation.blockingIssues()) {
            findings.add(new ReportPreflightFinding(
                    "DETERMINISTIC",
                    issue.code(),
                    "HIGH",
                    issue.resourceKey(),
                    issue.message(),
                    issue.resourceType(),
                    Map.of("resourceType", issue.resourceType())));
        }
        for (var warning : submitValidation.warnings()) {
            findings.add(new ReportPreflightFinding(
                    "DETERMINISTIC",
                    warning.code(),
                    "LOW",
                    warning.resourceKey(),
                    warning.message(),
                    warning.resourceType(),
                    Map.of("resourceType", warning.resourceType())));
        }
        validateTargetPresence(report, findings);
        validateConstructionDailyLog(report, findings);
        validatePhotoEvidenceStatus(report, findings);
        return new ReportPreflightValidationResult(findings);
    }

    private void validateReportState(InspectionReport report, ArrayList<ReportPreflightFinding> findings) {
        if (report.status() == InspectionReportStatus.CANCELLED) {
            findings.add(new ReportPreflightFinding(
                    "DETERMINISTIC",
                    "REPORT_CANCELLED",
                    "HIGH",
                    "report.status",
                    "취소된 리포트는 문서 생성 전 검토를 진행할 수 없습니다.",
                    "status=" + report.status().name(),
                    Map.of("status", report.status().name())));
        }
        if (report.status() == InspectionReportStatus.GENERATION_REQUESTED
                || report.status() == InspectionReportStatus.GENERATING) {
            findings.add(new ReportPreflightFinding(
                    "DETERMINISTIC",
                    "REPORT_GENERATION_IN_PROGRESS",
                    "HIGH",
                    "report.status",
                    "문서 생성이 진행 중인 리포트는 사전 검토를 다시 시작할 수 없습니다.",
                    "status=" + report.status().name(),
                    Map.of("status", report.status().name())));
        }
    }

    private void validateTargetPresence(InspectionReport report, ArrayList<ReportPreflightFinding> findings) {
        var policy = targetPolicy(report.reportType());
        if (policy.mode() == TargetRequirementMode.NONE) {
            return;
        }
        var targets = targetRepository.findByOfficeIdAndReportIdOrderByRoleAscIdAsc(report.officeId(), report.id());
        if (targets.isEmpty()) {
            findings.add(new ReportPreflightFinding(
                    "DETERMINISTIC",
                    "REPORT_TARGET_NOT_SELECTED",
                    policy.severity(),
                    "report.targets",
                    policy.message(),
                    "No inspection target is linked to the report",
                    Map.of(
                            "reportType", normalizeReportType(report.reportType()),
                            "targetPolicy", policy.mode().name())));
        }
    }

    private static TargetPolicy targetPolicy(String reportType) {
        return switch (normalizeReportType(reportType)) {
            case "CONSTRUCTION_DAILY_SUPERVISION_LOG" ->
                    new TargetPolicy(TargetRequirementMode.NONE, "NONE", "");
            default ->
                    new TargetPolicy(TargetRequirementMode.NONE, "NONE", "");
        };
    }

    private void validateConstructionDailyLog(InspectionReport report, ArrayList<ReportPreflightFinding> findings) {
        if (!"CONSTRUCTION_DAILY_SUPERVISION_LOG".equals(normalizeReportType(report.reportType()))) {
            return;
        }
        var step = stepRepository.findByReportIdAndStepCode(report.id(), "DAILY_LOG");
        if (step.isEmpty()) {
            return;
        }
        var dailyItems = dailyItems(step.get());
        if (dailyItems.isEmpty()) {
            findings.add(finding(
                    "DAILY_LOG_ITEMS_INVALID",
                    "HIGH",
                    "steps.DAILY_LOG.payload.dailyItems",
                    "공사감리일지의 공종별 검사항목 데이터 형식을 읽을 수 없습니다.",
                    "dailyItems is missing or invalid",
                    Map.of("reportType", normalizeReportType(report.reportType()))));
            return;
        }
        var groups = listValue(dailyItems.get().get("groups"));
        if (groups.isEmpty()) {
            findings.add(finding(
                    "DAILY_LOG_GROUP_REQUIRED",
                    "HIGH",
                    "steps.DAILY_LOG.payload.dailyItems.groups",
                    "공사감리일지는 최소 1개 이상의 공종/세부공정 항목이 필요합니다.",
                    "dailyItems.groups is empty",
                    Map.of("reportType", normalizeReportType(report.reportType()))));
            return;
        }

        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            var group = mapValue(groups.get(groupIndex));
            var groupNo = groupIndex + 1;
            requireText(group, "floor", "DAILY_LOG_GROUP_FLOOR_REQUIRED", "HIGH",
                    "층/구역을 입력해야 공사감리일지의 공종 및 세부공정 칸을 완성할 수 있습니다.",
                    "groups[" + groupIndex + "].floor", groupNo, null, findings);
            if (isBlank(stringValue(group.get("tradeName"))) && isBlank(stringValue(group.get("tradeCode")))) {
                findings.add(dailyFinding(
                        "DAILY_LOG_GROUP_TRADE_REQUIRED",
                        "HIGH",
                        "공종을 선택하거나 입력해야 합니다.",
                        "groups[" + groupIndex + "].tradeName",
                        groupNo,
                        null));
            } else if (isBlank(stringValue(group.get("tradeCode")))) {
                findings.add(dailyFinding(
                        "DAILY_LOG_GROUP_TRADE_CODE_REQUIRED",
                        "HIGH",
                        "공종을 카탈로그에서 선택해야 법령 근거를 연결할 수 있습니다.",
                        "groups[" + groupIndex + "].tradeCode",
                        groupNo,
                        null));
            }
            if (isBlank(stringValue(group.get("processName"))) && isBlank(stringValue(group.get("processCode")))) {
                findings.add(dailyFinding(
                        "DAILY_LOG_GROUP_PROCESS_REQUIRED",
                        "HIGH",
                        "세부공정을 입력해야 합니다.",
                        "groups[" + groupIndex + "].processName",
                        groupNo,
                        null));
            } else if (isBlank(stringValue(group.get("processCode")))) {
                findings.add(dailyFinding(
                        "DAILY_LOG_GROUP_PROCESS_CODE_REQUIRED",
                        "HIGH",
                        "세부공정을 카탈로그에서 선택해야 법령 근거를 연결할 수 있습니다.",
                        "groups[" + groupIndex + "].processCode",
                        groupNo,
                        null));
            }

            var entries = listValue(group.get("entries"));
            if (entries.isEmpty()) {
                findings.add(dailyFinding(
                        "DAILY_LOG_ENTRY_REQUIRED",
                        "HIGH",
                        "공종/세부공정마다 최소 1개 이상의 검사항목이 필요합니다.",
                        "groups[" + groupIndex + "].entries",
                        groupNo,
                        null));
                continue;
            }

            for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                var entry = mapValue(entries.get(entryIndex));
                var entryNo = entryIndex + 1;
                if (isBlank(stringValue(entry.get("inspectionItemName"))) && isBlank(stringValue(entry.get("inspectionItemCode")))) {
                    findings.add(dailyFinding(
                            "DAILY_LOG_INSPECTION_ITEM_REQUIRED",
                            "HIGH",
                            "검사항목을 선택하거나 입력해야 합니다.",
                            "groups[" + groupIndex + "].entries[" + entryIndex + "].inspectionItemName",
                            groupNo,
                            entryNo));
                } else if (isBlank(stringValue(entry.get("inspectionItemCode")))) {
                    findings.add(dailyFinding(
                            "DAILY_LOG_INSPECTION_ITEM_CODE_REQUIRED",
                            "HIGH",
                            "검사항목을 카탈로그에서 선택해야 법령 근거를 연결할 수 있습니다.",
                            "groups[" + groupIndex + "].entries[" + entryIndex + "].inspectionItemCode",
                            groupNo,
                            entryNo));
                }
                var supervisionContentLocation = "groups[" + groupIndex + "].entries[" + entryIndex + "].supervisionContent";
                var supervisionContent = dailySupervisionContent(entry);
                if (isBlank(supervisionContent)) {
                    findings.add(dailyFinding(
                            "DAILY_LOG_SUPERVISION_CONTENT_REQUIRED",
                            "HIGH",
                            "세부 감리항목에서 적합/부적합 또는 기준·참고사항/조치사항을 입력해야 문서 생성 시 감리내용 칸이 비지 않습니다.",
                            supervisionContentLocation,
                            groupNo,
                            entryNo));
                }
                findings.addAll(ReportPreflightWordingLint.dailySupervisionContent(
                        supervisionContent,
                        "steps.DAILY_LOG.payload.dailyItems." + supervisionContentLocation,
                        groupNo,
                        entryNo));
                if (dailyEntryPhotoIds(entry).isEmpty()) {
                    findings.add(dailyFinding(
                            "DAILY_LOG_PHOTO_EVIDENCE_RECOMMENDED",
                            "LOW",
                            "검사항목에 연결된 사진이 없습니다. 현장 확인 근거가 필요한 항목이면 사진을 연결하세요.",
                            "groups[" + groupIndex + "].entries[" + entryIndex + "].photoIds",
                            groupNo,
                            entryNo));
                }
            }
        }
    }

    private void validatePhotoEvidenceStatus(InspectionReport report, ArrayList<ReportPreflightFinding> findings) {
        if (!"CONSTRUCTION_DAILY_SUPERVISION_LOG".equals(normalizeReportType(report.reportType()))) {
            return;
        }
        var status = photoEvidenceStatusService.evaluate(report);
        var baseAttributes = new java.util.LinkedHashMap<String, String>();
        baseAttributes.put("reportType", "CONSTRUCTION_DAILY_SUPERVISION_LOG");
        baseAttributes.put("photoEvidenceStatus", status.toMap().toString());
        if (!status.missingDailyLogPhotoIds().isEmpty()) {
            findings.add(finding(
                    "DAILY_LOG_PHOTO_REFERENCE_MISSING",
                    "HIGH",
                    "steps.DAILY_LOG.payload.dailyItems.photoIds",
                    "공정별 검사항목이 참조하는 사진이 실제 리포트 사진 목록에 없습니다.",
                    "missingDailyLogPhotoIds=" + status.missingDailyLogPhotoIds(),
                    attributes(baseAttributes, "missingDailyLogPhotoIds", status.missingDailyLogPhotoIds().toString())));
        }
        if (!status.pendingDailyLogPhotoIds().isEmpty()) {
            findings.add(finding(
                    "DAILY_LOG_PHOTO_UPLOAD_PENDING",
                    "HIGH",
                    "steps.DAILY_LOG.payload.dailyItems.photoIds",
                    "공정별 검사항목에 연결된 사진 중 업로드가 완료되지 않은 사진이 있습니다.",
                    "pendingDailyLogPhotoIds=" + status.pendingDailyLogPhotoIds(),
                    attributes(baseAttributes, "pendingDailyLogPhotoIds", status.pendingDailyLogPhotoIds().toString())));
        }
        if (!status.notWorkingUploadedPhotoIds().isEmpty()) {
            findings.add(finding(
                    "PHOTO_WORKING_ASSET_NOT_READY",
                    "HIGH",
                    "photos.assets.WORKING",
                    "문서 생성에 사용할 작업본 사진이 아직 준비되지 않았습니다.",
                    "notWorkingUploadedPhotoIds=" + status.notWorkingUploadedPhotoIds(),
                    attributes(baseAttributes, "notWorkingUploadedPhotoIds", status.notWorkingUploadedPhotoIds().toString())));
        }
        if (!status.unlinkedPhotoIds().isEmpty()) {
            findings.add(finding(
                    "REPORT_PHOTO_UNLINKED_TO_DAILY_LOG",
                    "LOW",
                    "photos.unlinked",
                    "리포트에 업로드되었지만 공정별 검사항목에 연결되지 않은 사진이 있습니다.",
                    "unlinkedPhotoIds=" + status.unlinkedPhotoIds(),
                    attributes(baseAttributes, "unlinkedPhotoIds", status.unlinkedPhotoIds().toString())));
        }
    }

    private Optional<Map<String, Object>> dailyItems(InspectionReportStep step) {
        var payload = step.payloadJson();
        if (payload == null) {
            return Optional.empty();
        }
        return normalizedMap(payload.get("dailyItems"));
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> normalizedMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return Optional.of((Map<String, Object>) map);
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Optional.of(objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {
                }));
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private String dailySupervisionContent(Map<String, Object> entry) {
        var rows = new ArrayList<String>();
        for (Object rowValue : listValue(entry.get("checklistRows"))) {
            var row = mapValue(rowValue);
            var rowContent = checklistRowContent(row);
            if (!rowContent.isBlank()) {
                rows.add("- " + rowContent);
            }
        }
        if (rows.isEmpty()) {
            return "";
        }
        var title = stringValue(entry.get("inspectionItemName"));
        if (!title.isBlank()) {
            rows.add(0, title);
        }
        var rowContent = String.join("\n", rows);
        return rowContent.isBlank() ? stringValue(entry.get("supervisionContent")) : rowContent;
    }

    private List<?> dailyEntryPhotoIds(Map<String, Object> entry) {
        var photoIds = new ArrayList<Object>();
        for (Object rowValue : listValue(entry.get("checklistRows"))) {
            photoIds.addAll(listValue(mapValue(rowValue).get("photoIds")));
        }
        return photoIds;
    }

    private String checklistRowContent(Map<String, Object> row) {
        var label = stringValue(row.get("label"));
        var result = checklistResultLabel(stringValue(row.get("result")));
        var referenceNote = stringValue(row.get("referenceNote"));
        var actionNote = stringValue(row.get("actionNote"));
        var parts = new ArrayList<String>();
        if (!label.isBlank()) {
            parts.add(label);
        }
        if (!result.isBlank()) {
            parts.add(result);
        }
        if (!referenceNote.isBlank()) {
            parts.add("기준·참고: " + referenceNote);
        }
        if (!actionNote.isBlank()) {
            parts.add("조치사항: " + actionNote);
        }
        return parts.size() <= 1 ? "" : String.join(" / ", parts);
    }

    private String checklistResultLabel(String result) {
        return switch (result.trim().toUpperCase(Locale.ROOT)) {
            case "COMPLIANT" -> "적합";
            case "NON_COMPLIANT" -> "부적합";
            default -> "";
        };
    }

    private void requireText(
            Map<String, Object> payload,
            String key,
            String code,
            String severity,
            String message,
            String location,
            int groupNo,
            Integer entryNo,
            ArrayList<ReportPreflightFinding> findings
    ) {
        if (isBlank(stringValue(payload.get(key)))) {
            findings.add(dailyFinding(code, severity, message, location, groupNo, entryNo));
        }
    }

    private ReportPreflightFinding dailyFinding(
            String code,
            String severity,
            String message,
            String location,
            int groupNo,
            Integer entryNo
    ) {
        var attributes = new java.util.LinkedHashMap<String, String>();
        attributes.put("reportType", "CONSTRUCTION_DAILY_SUPERVISION_LOG");
        attributes.put("groupNo", String.valueOf(groupNo));
        if (entryNo != null) {
            attributes.put("entryNo", String.valueOf(entryNo));
        }
        return finding(
                code,
                severity,
                "steps.DAILY_LOG.payload.dailyItems." + location,
                message,
                "groupNo=" + groupNo + (entryNo == null ? "" : ", entryNo=" + entryNo),
                attributes);
    }

    private ReportPreflightFinding finding(
            String code,
            String severity,
            String location,
            String message,
            String evidence,
            Map<String, String> attributes
    ) {
        return new ReportPreflightFinding(
                "DETERMINISTIC",
                code,
                severity,
                location,
                message,
                evidence,
                attributes);
    }

    private Map<String, String> attributes(Map<String, String> base, String key, String value) {
        var attributes = new java.util.LinkedHashMap<>(base);
        attributes.put(key, value);
        return Map.copyOf(attributes);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalizeReportType(String reportType) {
        return reportType == null ? "" : reportType.trim().toUpperCase(Locale.ROOT);
    }

    private enum TargetRequirementMode {
        NONE,
        RECOMMENDED,
        REQUIRED
    }

    private record TargetPolicy(
            TargetRequirementMode mode,
            String severity,
            String message
    ) {
    }
}
