package com.archdox.cloud.reportai.application;

import com.archdox.cloud.engine.application.EngineValidationResult;
import com.archdox.cloud.engine.application.EngineValidationService;
import com.archdox.cloud.engine.domain.EngineReviewSession;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.domain.InspectionReportStep;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.photo.domain.Photo;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ReportPreflightEngineBoundaryService {
    private static final String DAILY_LOG_STEP_CODE = "DAILY_LOG";
    private static final String DAILY_ITEMS_FIELD = "dailyItems";

    private final InspectionReportStepRepository stepRepository;
    private final PhotoRepository photoRepository;
    private final EngineValidationService validationService;
    private final ObjectMapper objectMapper;

    public ReportPreflightEngineBoundaryService(
            InspectionReportStepRepository stepRepository,
            PhotoRepository photoRepository,
            EngineValidationService validationService,
            ObjectMapper objectMapper
    ) {
        this.stepRepository = stepRepository;
        this.photoRepository = photoRepository;
        this.validationService = validationService;
        this.objectMapper = objectMapper;
    }

    public EngineValidationResult validate(InspectionReport report, Long requestedBy) {
        var now = OffsetDateTime.now();
        var session = new EngineReviewSession(
                "saas_report_" + report.id() + "_rev_" + report.contentRevision(),
                requestedBy == null ? report.requestedBy() : requestedBy,
                report.officeId(),
                "archdox-report:" + report.id(),
                report.reportType(),
                now);
        session.submitDocument(
                report.reportType(),
                report.reportNo() + ".snapshot",
                report.title() == null ? report.reportType() : report.title(),
                now);
        return validationService.validate(session, normalizedContext(report));
    }

    private Map<String, Object> normalizedContext(InspectionReport report) {
        var values = new LinkedHashMap<String, Object>();
        putValue(values, "reportType", report.reportType());
        putValue(values, "officeId", report.officeId());
        putValue(values, "projectId", report.projectId());
        putValue(values, "siteId", report.siteId());
        putValue(values, "reportId", report.id());
        putValue(values, "reportRevision", report.contentRevision());

        var photos = photoRepository.findByOfficeIdAndReportIdAndStatusNotOrderByIdDesc(
                report.officeId(),
                report.id(),
                PhotoStatus.DELETED);
        if (!photos.isEmpty()) {
            putValue(values, "photoIds", photos.stream().map(Photo::id).map(String::valueOf).toList());
            putValue(values, "photoEvidence", "photos:" + photos.size());
        }

        var catalogSelections = new ArrayList<Map<String, Object>>();
        var supervisionContent = new ArrayList<String>();
        var floors = new ArrayList<String>();
        dailyItems(report).ifPresent(dailyItems -> collectDailyLogContext(
                dailyItems,
                catalogSelections,
                supervisionContent,
                floors));
        if (!catalogSelections.isEmpty()) {
            putValue(values, "workType", "CONSTRUCTION_SUPERVISION");
        }
        if (!supervisionContent.isEmpty()) {
            putValue(values, "supervisionContent", String.join("\n", supervisionContent));
            putValue(values, "evidenceText", String.join("\n", supervisionContent));
        }
        if (!floors.isEmpty()) {
            putValue(values, "floor", String.join(", ", floors.stream().distinct().toList()));
            putValue(values, "workArea", String.join(", ", floors.stream().distinct().toList()));
        }
        collectNarrativeContext(report, values);

        var context = new LinkedHashMap<String, Object>();
        context.put("values", Map.copyOf(values));
        context.put("missingFields", List.of());
        context.put("missingQuestions", List.of());
        context.put("ambiguities", List.of());
        context.put("catalogSelections", List.copyOf(catalogSelections));
        context.put("source", "ARCHDOX_SAAS_REPORT_PREFLIGHT");
        return Map.copyOf(context);
    }

    private void collectNarrativeContext(
            InspectionReport report,
            Map<String, Object> values
    ) {
        stepRepository.findByReportIdAndStepCode(report.id(), "DAILY_LOG")
                .map(InspectionReportStep::payloadJson)
                .ifPresent(payload -> {
                    putValue(values, "issueAndAction", firstNonBlank(
                            text(payload.get("issueAndAction")),
                            text(payload.get("issueAndActionResult"))));
                    putValue(values, "nextAction", text(payload.get("nextAction")));
                });
        stepRepository.findByReportIdAndStepCode(report.id(), "REMARKS")
                .map(InspectionReportStep::payloadJson)
                .ifPresent(payload -> {
                    var remarks = firstNonBlank(
                            text(payload.get("remarks")),
                            text(payload.get("specialNotes")));
                    putValue(values, "remarks", remarks);
                    putValue(values, "specialNotes", remarks);
                    putValue(values, "issueAndAction", text(payload.get("issueAndAction")));
                    putValue(values, "nextAction", text(payload.get("nextAction")));
                });
    }

    private Optional<Map<String, Object>> dailyItems(InspectionReport report) {
        if (!"CONSTRUCTION_DAILY_SUPERVISION_LOG".equals(report.reportType())) {
            return Optional.empty();
        }
        return stepRepository.findByReportIdAndStepCode(report.id(), DAILY_LOG_STEP_CODE)
                .flatMap(step -> normalizedDailyItems(step.payloadJson()));
    }

    private void collectDailyLogContext(
            Map<String, Object> dailyItems,
            List<Map<String, Object>> catalogSelections,
            List<String> supervisionContent,
            List<String> floors
    ) {
        var groups = listValue(dailyItems.get("groups"));
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            var group = mapValue(groups.get(groupIndex));
            var groupNo = groupIndex + 1;
            var floor = text(group.get("floor"));
            if (!floor.isBlank()) {
                floors.add(floor);
            }
            var entries = listValue(group.get("entries"));
            for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                var entry = mapValue(entries.get(entryIndex));
                var entryNo = entryIndex + 1;
                var selection = new LinkedHashMap<String, Object>();
                selection.put("tradeCode", text(group.get("tradeCode")));
                selection.put("processCode", text(group.get("processCode")));
                selection.put("inspectionItemCode", text(entry.get("inspectionItemCode")));
                selection.put("location", "steps.DAILY_LOG.payload.dailyItems.groups[" + groupIndex + "].entries[" + entryIndex + "]");
                selection.put("sourceRef", "daily-log:g" + groupNo + ":e" + entryNo);
                selection.put("groupNo", groupNo);
                selection.put("entryNo", entryNo);
                if (!floor.isBlank()) {
                    selection.put("floor", floor);
                }
                catalogSelections.add(Map.copyOf(selection));

                var content = dailySupervisionContent(entry);
                if (!content.isBlank()) {
                    supervisionContent.add(content);
                }
            }
        }
    }

    private String dailySupervisionContent(Map<String, Object> entry) {
        var content = text(entry.get("supervisionContent"));
        if (!content.isBlank()) {
            return content;
        }
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
        var title = text(entry.get("inspectionItemName"));
        if (!title.isBlank()) {
            rows.add(0, title);
        }
        return String.join("\n", rows);
    }

    private String checklistRowContent(Map<String, Object> row) {
        var label = text(row.get("label"));
        var result = checklistResultLabel(text(row.get("result")));
        var referenceNote = text(row.get("referenceNote"));
        var actionNote = text(row.get("actionNote"));
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
        return switch (result.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "COMPLIANT" -> "적합";
            case "NON_COMPLIANT" -> "부적합";
            default -> "";
        };
    }

    private Optional<Map<String, Object>> normalizedDailyItems(Map<String, Object> payload) {
        if (payload == null) {
            return Optional.empty();
        }
        return normalizedMap(payload.get(DAILY_ITEMS_FIELD));
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

    private void putValue(Map<String, Object> values, String fieldName, Object value) {
        if (value == null) {
            return;
        }
        var text = value instanceof List<?> list
                ? String.join(",", list.stream().map(String::valueOf).toList())
                : String.valueOf(value).trim();
        if (text.isBlank()) {
            return;
        }
        values.put(fieldName, Map.of(
                "fieldName", fieldName,
                "canonicalValue", text,
                "rawValue", text,
                "confidence", 1.0d));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }
}
