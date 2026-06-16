package com.archdox.cloud.engine.inspection;

import com.archdox.cloud.engine.dto.EngineContextFactRequest;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.supervisioncatalog.application.SupervisionDomainCatalogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class InspectionDocumentTextExtractionService {
    private static final String SOURCE = "CUSTOMER_AGENT_EXTRACTED";
    private static final Pattern DATE_PATTERN =
            Pattern.compile("(20\\d{2})\\s*년\\s*(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
    private static final Pattern NUMERIC_DATE_PATTERN =
            Pattern.compile("(20\\d{2})\\s*[.\\-/\\s]\\s*(\\d{1,2})\\s*[.\\-/\\s]\\s*(\\d{1,2})");
    private static final Pattern WEATHER_PATTERN =
            Pattern.compile("날씨\\s*[:：]?\\s*([^\\s\\n\\r]+)");

    private final SupervisionDomainCatalogService catalogService;
    private final ObjectMapper objectMapper;

    public InspectionDocumentTextExtractionService(
            SupervisionDomainCatalogService catalogService,
            ObjectMapper objectMapper
    ) {
        this.catalogService = catalogService;
        this.objectMapper = objectMapper;
    }

    public InspectionDocumentExtractionResult extract(
            String contentText,
            String targetDate,
            String documentTypeHint
    ) {
        if (contentText == null || contentText.isBlank()) {
            throw new BadRequestException("contentText is required");
        }
        var block = selectTargetBlock(contentText, targetDate);
        var catalogSelections = catalogSelections(block);
        var facts = new ArrayList<EngineContextFactRequest>();
        addFact(facts, "reportType", firstNonBlank(documentTypeHint, "CONSTRUCTION_DAILY_SUPERVISION_LOG"),
                "documentTypeHint", 0.90d);
        extractDate(block).ifPresent(date -> addFact(facts, "inspectionDate", date.toString(), "daily log date", 0.92d));
        extractProjectName(block).ifPresent(projectName -> addFact(facts, "projectName", projectName, "daily log project name", 0.85d));
        extractWeather(block).ifPresent(weather -> addFact(facts, "weather", weather, "daily log weather", 0.85d));
        extractWorkArea(block).ifPresent(workArea -> {
            addFact(facts, "workArea", workArea, "daily log work area", 0.88d);
            addFact(facts, "floor", workArea, "daily log work area", 0.80d);
        });
        if (!catalogSelections.isEmpty()) {
            addFact(facts, "workType", "CONSTRUCTION_SUPERVISION", "catalog selections extracted from daily log", 0.82d);
            addFact(facts, "catalogSelections", writeJson(catalogSelections), "daily log catalog selection extraction", 0.90d);
            var first = catalogSelections.getFirst();
            addFact(facts, "tradeCode", text(first.get("tradeCode")), "first extracted catalog selection", 0.75d);
            addFact(facts, "processCode", text(first.get("processCode")), "first extracted catalog selection", 0.75d);
            addFact(facts, "inspectionItemCode", text(first.get("inspectionItemCode")), "first extracted catalog selection", 0.75d);
        }
        var supervisionContent = supervisionContent(block, catalogSelections);
        if (!supervisionContent.isBlank()) {
            addFact(facts, "supervisionContent", supervisionContent, "daily log extracted supervision content", 0.86d);
            addFact(facts, "evidenceText", supervisionContent, "daily log extracted supervision content", 0.80d);
        }

        var metadata = new LinkedHashMap<String, Object>();
        var normalizedTargetDate = normalizeTargetDate(targetDate);
        var availableDates = availableDates(contentText);
        metadata.put("targetDate", normalizedTargetDate);
        metadata.put("targetDateMatched", targetDateMatched(availableDates, normalizedTargetDate));
        metadata.put("availableDates", availableDates);
        metadata.put("selectedTextPreview", abbreviate(block, 1200));
        metadata.put("catalogSelectionCount", catalogSelections.size());
        metadata.put("extractedFactCount", facts.size());

        return new InspectionDocumentExtractionResult(
                List.copyOf(facts),
                List.copyOf(catalogSelections),
                Map.copyOf(metadata));
    }

    private String selectTargetBlock(String contentText, String targetDate) {
        var normalizedDate = normalizeTargetDate(targetDate);
        if (normalizedDate.isBlank()) {
            return contentText.trim();
        }
        var marker = koreanDateMarker(normalizedDate);
        var index = contentText.indexOf(marker);
        if (index < 0) {
            var blocks = contentText.split("(?=■)");
            for (var block : blocks) {
                if (availableDates(block).contains(normalizedDate)) {
                    return block.trim();
                }
            }
        }
        if (index < 0) {
            return contentText.trim();
        }
        var start = Math.max(0, contentText.lastIndexOf("■", index));
        var next = contentText.indexOf("■", Math.min(contentText.length(), index + marker.length()));
        if (next <= start) {
            next = contentText.length();
        }
        return contentText.substring(start, next).trim();
    }

    private List<Map<String, Object>> catalogSelections(String block) {
        var normalizedBlock = normalizeMatchText(block);
        var catalog = catalogService.get(catalogService.defaultConstructionCatalogCode());
        var selections = new ArrayList<Map<String, Object>>();
        var seen = new LinkedHashSet<String>();
        var trades = catalog.path("trades");
        if (!trades.isArray()) {
            return List.of();
        }
        for (var trade : trades) {
            var tradeCode = text(trade.path("code"));
            var tradeName = text(trade.path("name"));
            var tradeMentioned = containsNormalized(normalizedBlock, tradeName);
            var processGroups = trade.path("processGroups");
            if (!processGroups.isArray()) {
                continue;
            }
            for (var process : processGroups) {
                var processCode = text(process.path("code"));
                var processName = text(process.path("name"));
                var items = process.path("items");
                if (!items.isArray()) {
                    continue;
                }
                for (var item : items) {
                    var itemName = text(item.path("name"));
                    var basis = text(item.path("basis"));
                    var itemMentioned = containsNormalized(normalizedBlock, itemName);
                    var exactItemStrongEnoughWithoutTrade = itemMentioned && normalizeMatchText(itemName).length() >= 5;
                    if (!tradeMentioned && !exactItemStrongEnoughWithoutTrade) {
                        continue;
                    }
                    if (!itemMentioned && !containsMeaningfulBasis(normalizedBlock, basis)) {
                        continue;
                    }
                    var inspectionItemCode = text(item.path("code"));
                    var key = tradeCode + "/" + processCode + "/" + inspectionItemCode;
                    if (!seen.add(key)) {
                        continue;
                    }
                    var selection = new LinkedHashMap<String, Object>();
                    selection.put("tradeCode", tradeCode);
                    selection.put("tradeName", tradeName);
                    selection.put("processCode", processCode);
                    selection.put("processName", processName);
                    selection.put("inspectionItemCode", inspectionItemCode);
                    selection.put("inspectionItemName", itemName);
                    selection.put("basis", basis);
                    selection.put("location", "extracted.dailyLog.catalogSelections[" + selections.size() + "]");
                    selections.add(Map.copyOf(selection));
                }
            }
        }
        return selections.stream().limit(20).toList();
    }

    private boolean containsMeaningfulBasis(String normalizedBlock, String basis) {
        var normalizedBasis = normalizeMatchText(basis);
        if (normalizedBasis.length() < 5) {
            return false;
        }
        return normalizedBlock.contains(normalizedBasis);
    }

    private java.util.Optional<LocalDate> extractDate(String block) {
        var matcher = DATE_PATTERN.matcher(block);
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(LocalDate.of(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))));
    }

    private java.util.Optional<String> extractProjectName(String block) {
        var marker = "공사명";
        var index = block.indexOf(marker);
        if (index < 0) {
            return java.util.Optional.empty();
        }
        var after = block.substring(index + marker.length()).trim();
        var dateMatcher = DATE_PATTERN.matcher(after);
        if (dateMatcher.find()) {
            var value = after.substring(0, dateMatcher.start()).trim();
            return value.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(oneLine(value));
        }
        var line = after.lines().map(String::trim).filter(value -> !value.isBlank()).findFirst().orElse("");
        return line.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(line);
    }

    private java.util.Optional<String> extractWeather(String block) {
        var matcher = WEATHER_PATTERN.matcher(block);
        if (matcher.find()) {
            return java.util.Optional.of(matcher.group(1).trim());
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<String> extractWorkArea(String block) {
        var lines = block.lines().map(String::trim).filter(value -> !value.isBlank()).toList();
        for (var line : lines) {
            if (line.startsWith("(") && line.endsWith(")") && line.length() <= 40) {
                return java.util.Optional.of(line.substring(1, line.length() - 1).trim());
            }
        }
        return java.util.Optional.empty();
    }

    private String supervisionContent(String block, List<Map<String, Object>> catalogSelections) {
        if (catalogSelections.isEmpty()) {
            return abbreviate(oneLine(block), 1000);
        }
        var parts = catalogSelections.stream()
                .map(selection -> text(selection.get("tradeName"))
                        + " / "
                        + text(selection.get("inspectionItemName"))
                        + ": "
                        + firstNonBlank(text(selection.get("basis")), "감리일지 본문에서 해당 항목을 확인했습니다."))
                .distinct()
                .limit(12)
                .toList();
        return String.join("\n", parts);
    }

    private void addFact(
            List<EngineContextFactRequest> facts,
            String fieldName,
            String rawValue,
            String evidence,
            double confidence
    ) {
        if (rawValue == null || rawValue.isBlank()) {
            return;
        }
        facts.add(new EngineContextFactRequest(fieldName, fieldName, rawValue.trim(), SOURCE, evidence, confidence));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize extracted catalog selections", ex);
        }
    }

    private String normalizeTargetDate(String targetDate) {
        if (targetDate == null || targetDate.isBlank()) {
            return "";
        }
        var value = targetDate.trim();
        try {
            return LocalDate.parse(value).toString();
        } catch (DateTimeParseException ignored) {
            var matcher = DATE_PATTERN.matcher(value);
            if (matcher.find()) {
                return parseDate(matcher.group(1), matcher.group(2), matcher.group(3));
            }
            matcher = NUMERIC_DATE_PATTERN.matcher(value);
            if (matcher.find()) {
                return parseDate(matcher.group(1), matcher.group(2), matcher.group(3));
            }
            return value;
        }
    }

    private String koreanDateMarker(String isoDate) {
        try {
            var date = LocalDate.parse(isoDate);
            return "%d년 %d월 %d일".formatted(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
        } catch (DateTimeParseException ignored) {
            return isoDate;
        }
    }

    private boolean targetDateMatched(List<String> availableDates, String normalizedTargetDate) {
        if (availableDates == null || availableDates.isEmpty() || normalizedTargetDate.isBlank()) {
            return false;
        }
        return availableDates.contains(normalizedTargetDate);
    }

    private List<String> availableDates(String contentText) {
        if (contentText == null || contentText.isBlank()) {
            return List.of();
        }
        var dates = new LinkedHashSet<String>();
        collectDates(dates, DATE_PATTERN, contentText);
        collectDates(dates, NUMERIC_DATE_PATTERN, contentText);
        return List.copyOf(dates);
    }

    private void collectDates(LinkedHashSet<String> dates, Pattern pattern, String contentText) {
        var matcher = pattern.matcher(contentText);
        while (matcher.find()) {
            if (isFormRevisionDateContext(contentText, matcher.start(), matcher.end())) {
                continue;
            }
            var date = parseDate(matcher.group(1), matcher.group(2), matcher.group(3));
            if (!date.isBlank()) {
                dates.add(date);
            }
        }
    }

    private boolean isFormRevisionDateContext(String contentText, int start, int end) {
        var from = Math.max(0, start - 24);
        var to = Math.min(contentText.length(), end + 24);
        var context = contentText.substring(from, to).toLowerCase(Locale.ROOT);
        return context.contains("\uac1c\uc815")
                || context.contains("\uc81c\uc815")
                || context.contains("revised")
                || context.contains("amended");
    }

    private String parseDate(String year, String month, String day) {
        try {
            return LocalDate.of(
                    Integer.parseInt(year),
                    Integer.parseInt(month),
                    Integer.parseInt(day)).toString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private boolean containsNormalized(String haystack, String needle) {
        var normalizedNeedle = normalizeMatchText(needle);
        return !normalizedNeedle.isBlank() && haystack.contains(normalizedNeedle);
    }

    private String normalizeMatchText(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace("의", "")
                .replaceAll("[\\s\\p{Punct}·ㆍ․,，()\\[\\]{}<>〔〕-]+", "");
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    private String oneLine(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String abbreviate(String value, int maxLength) {
        var text = value == null ? "" : value.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("").trim();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record InspectionDocumentExtractionResult(
            List<EngineContextFactRequest> facts,
            List<Map<String, Object>> catalogSelections,
            Map<String, Object> metadata
    ) {
        public InspectionDocumentExtractionResult {
            facts = facts == null ? List.of() : List.copyOf(facts);
            catalogSelections = catalogSelections == null ? List.of() : catalogSelections.stream().map(Map::copyOf).toList();
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
