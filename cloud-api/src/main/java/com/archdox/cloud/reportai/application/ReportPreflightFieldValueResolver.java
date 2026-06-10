package com.archdox.cloud.reportai.application;

import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ReportPreflightFieldValueResolver {
    private static final Pattern DAILY_GROUPED_SUPERVISION_CONTENT = Pattern.compile(
            ".*groups\\[(\\d+)]\\.entries\\[(\\d+)]\\.supervisionContent$");
    private static final Pattern DAILY_FLAT_SUPERVISION_CONTENT = Pattern.compile(
            ".*DAILY_LOG\\.entries\\[(\\d+)]\\.supervisionContent$");
    private static final Pattern REMARKS_DIRECT_FIELD = Pattern.compile(
            ".*REMARKS(?:\\.payload)?\\.(issueAndAction|nextAction)$");

    private final InspectionReportStepRepository stepRepository;
    private final ObjectMapper objectMapper;

    public ReportPreflightFieldValueResolver(
            InspectionReportStepRepository stepRepository,
            ObjectMapper objectMapper
    ) {
        this.stepRepository = stepRepository;
        this.objectMapper = objectMapper;
    }

    public Optional<String> resolve(Long reportId, String location) {
        var normalized = location == null ? "" : location.trim();
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        var remarksMatcher = REMARKS_DIRECT_FIELD.matcher(normalized);
        if (remarksMatcher.matches()) {
            return resolveRemarks(reportId, remarksMatcher.group(1));
        }
        var groupedDailyMatcher = DAILY_GROUPED_SUPERVISION_CONTENT.matcher(normalized);
        if (groupedDailyMatcher.matches()) {
            return resolveDailyGrouped(
                    reportId,
                    integerOrNull(groupedDailyMatcher.group(1)),
                    integerOrNull(groupedDailyMatcher.group(2)));
        }
        var flatDailyMatcher = DAILY_FLAT_SUPERVISION_CONTENT.matcher(normalized);
        if (flatDailyMatcher.matches()) {
            return resolveDailyFlat(reportId, integerOrNull(flatDailyMatcher.group(1)));
        }
        return Optional.empty();
    }

    public Optional<String> resolveHash(Long reportId, String location) {
        return resolve(reportId, location)
                .map(ReportPreflightFieldValueResolver::hashText);
    }

    public static String hashText(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizedText(value).getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available", ex);
        }
    }

    private Optional<String> resolveRemarks(Long reportId, String fieldName) {
        return stepRepository.findByReportIdAndStepCode(reportId, "REMARKS")
                .map(step -> text(step.payloadJson() == null ? null : step.payloadJson().get(fieldName)))
                .filter(value -> !value.isBlank());
    }

    private Optional<String> resolveDailyGrouped(Long reportId, Integer groupIndex, Integer entryIndex) {
        if (groupIndex == null || entryIndex == null || groupIndex < 0 || entryIndex < 0) {
            return Optional.empty();
        }
        return dailyItems(reportId)
                .flatMap(dailyItems -> {
                    var groups = listValue(dailyItems.get("groups"));
                    if (groupIndex >= groups.size()) {
                        return Optional.empty();
                    }
                    var entries = listValue(mapValue(groups.get(groupIndex)).get("entries"));
                    if (entryIndex >= entries.size()) {
                        return Optional.empty();
                    }
                    var value = text(mapValue(entries.get(entryIndex)).get("supervisionContent"));
                    return value.isBlank() ? Optional.empty() : Optional.of(value);
                });
    }

    private Optional<String> resolveDailyFlat(Long reportId, Integer flatEntryIndex) {
        if (flatEntryIndex == null || flatEntryIndex < 0) {
            return Optional.empty();
        }
        return dailyItems(reportId)
                .flatMap(dailyItems -> {
                    var remaining = flatEntryIndex;
                    for (Object groupValue : listValue(dailyItems.get("groups"))) {
                        var entries = listValue(mapValue(groupValue).get("entries"));
                        if (remaining < entries.size()) {
                            var value = text(mapValue(entries.get(remaining)).get("supervisionContent"));
                            return value.isBlank() ? Optional.empty() : Optional.of(value);
                        }
                        remaining -= entries.size();
                    }
                    return Optional.empty();
                });
    }

    private Optional<Map<String, Object>> dailyItems(Long reportId) {
        return stepRepository.findByReportIdAndStepCode(reportId, "DAILY_LOG")
                .flatMap(step -> normalizedMap(step.payloadJson() == null ? null : step.payloadJson().get("dailyItems")));
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
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static Integer integerOrNull(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizedText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
