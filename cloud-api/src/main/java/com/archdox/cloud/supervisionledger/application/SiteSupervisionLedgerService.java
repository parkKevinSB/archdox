package com.archdox.cloud.supervisionledger.application;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.domain.InspectionReportStep;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.office.application.OfficePermissionService;
import com.archdox.cloud.site.application.SiteService;
import com.archdox.cloud.supervisionledger.domain.SiteSupervisionEntry;
import com.archdox.cloud.supervisionledger.domain.SiteSupervisionEntryStatus;
import com.archdox.cloud.supervisionledger.dto.SiteSupervisionEntryResponse;
import com.archdox.cloud.supervisionledger.infra.SiteSupervisionEntryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SiteSupervisionLedgerService {
    private static final String DAILY_LOG_STEP = "DAILY_LOG";
    private static final String BASIC_INFO_STEP = "BASIC_INFO";
    private static final String SOURCE_TYPE_REPORT_STEP = "REPORT_STEP";
    private static final String CATALOG_CODE = "CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24";
    private static final int CATALOG_VERSION = 2;

    private final SiteSupervisionEntryRepository entryRepository;
    private final InspectionReportStepRepository stepRepository;
    private final SiteService siteService;
    private final OfficePermissionService permissionService;
    private final ObjectMapper objectMapper;

    public SiteSupervisionLedgerService(
            SiteSupervisionEntryRepository entryRepository,
            InspectionReportStepRepository stepRepository,
            SiteService siteService,
            OfficePermissionService permissionService,
            ObjectMapper objectMapper
    ) {
        this.entryRepository = entryRepository;
        this.stepRepository = stepRepository;
        this.siteService = siteService;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<SiteSupervisionEntryResponse> listEntries(Long projectId, Long siteId, UserPrincipal principal) {
        var site = siteService.requireSiteForProject(siteId, projectId);
        permissionService.requireActiveMembership(principal.userId(), site.officeId());
        var officeId = OfficeContext.requireCurrentOfficeId();
        return entryRepository.findByOfficeIdAndProjectIdAndSiteIdOrderByEntryDateDescUpdatedAtDescIdDesc(
                        officeId,
                        projectId,
                        siteId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void syncReportDailyLog(InspectionReport report, Long userId, OffsetDateTime now) {
        if (report.siteId() == null) {
            return;
        }
        var dailyStep = stepRepository.findByReportIdAndStepCode(report.id(), DAILY_LOG_STEP).orElse(null);
        if (dailyStep == null) {
            return;
        }
        var basicInfoStep = stepRepository.findByReportIdAndStepCode(report.id(), BASIC_INFO_STEP).orElse(null);
        var entryDate = resolveEntryDate(dailyStep, basicInfoStep, now);
        var entries = toEntries(report, dailyStep, entryDate, userId, now);
        entryRepository.deleteByOfficeIdAndSourceReportIdAndSourceReportRevisionAndSourceStepCode(
                report.officeId(),
                report.id(),
                report.contentRevision(),
                DAILY_LOG_STEP);
        if (!entries.isEmpty()) {
            entryRepository.saveAll(entries);
        }
    }

    @Transactional
    public void confirmReportRevision(InspectionReport report, Long userId, OffsetDateTime now) {
        entryRepository.updateStatusForReportRevision(
                report.officeId(),
                report.id(),
                report.contentRevision(),
                SiteSupervisionEntryStatus.CONFIRMED,
                userId,
                now);
    }

    private List<SiteSupervisionEntry> toEntries(
            InspectionReport report,
            InspectionReportStep dailyStep,
            LocalDate entryDate,
            Long userId,
            OffsetDateTime now
    ) {
        var groups = dailyGroups(dailyStep.payloadJson());
        var entries = new ArrayList<SiteSupervisionEntry>();
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            var group = groups.get(groupIndex);
            var items = group.path("items");
            if (!items.isArray()) {
                continue;
            }
            for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
                var item = items.get(itemIndex);
                var inspectionItemCode = firstNonBlank(
                        text(item, "inspectionItemCode"),
                        text(item, "itemCode"));
                var inspectionItemName = firstNonBlank(
                        text(item, "inspectionItemName"),
                        text(item, "item"));
                if (isBlank(text(group, "trade")) && isBlank(inspectionItemName)
                        && isBlank(text(item, "content")) && photoIds(item).isEmpty()) {
                    continue;
                }
                var sourceGroupKey = firstNonBlank(text(group, "id"), "group-" + groupIndex);
                var sourceItemKey = firstNonBlank(text(item, "id"), "item-" + itemIndex);
                entries.add(new SiteSupervisionEntry(
                        report.officeId(),
                        report.projectId(),
                        report.siteId(),
                        entryDate,
                        trimToNull(text(group, "floor")),
                        trimToNull(text(group, "tradeCode")),
                        trimToNull(text(group, "trade")),
                        trimToNull(text(group, "processCode")),
                        trimToNull(text(group, "process")),
                        trimToNull(text(item, "itemCode")),
                        trimToNull(text(item, "item")),
                        trimToNull(inspectionItemCode),
                        trimToNull(inspectionItemName),
                        trimToNull(text(item, "content")),
                        trimToNull(text(item, "resultStatus")),
                        trimToNull(text(item, "issueText")),
                        trimToNull(firstNonBlank(text(item, "actionResult"), text(item, "correctiveAction"))),
                        photoIds(item),
                        SiteSupervisionEntryStatus.DRAFT,
                        SOURCE_TYPE_REPORT_STEP,
                        report.id(),
                        report.contentRevision(),
                        DAILY_LOG_STEP,
                        dailyStep.clientRevision(),
                        sourceGroupKey,
                        sourceItemKey,
                        sourceGroupKey + ":" + sourceItemKey,
                        CATALOG_CODE,
                        CATALOG_VERSION,
                        userId,
                        now));
            }
        }
        return entries;
    }

    private List<JsonNode> dailyGroups(Map<String, Object> payload) {
        var rawDailyItems = payload == null ? null : payload.get("dailyItems");
        var root = readNode(rawDailyItems == null ? payload : rawDailyItems);
        if (root == null || root.isMissingNode() || root.isNull()) {
            return List.of();
        }
        var groups = root.path("groups");
        if (!groups.isArray()) {
            return List.of();
        }
        var result = new ArrayList<JsonNode>();
        groups.forEach(result::add);
        return result;
    }

    private JsonNode readNode(Object value) {
        if (value == null) {
            return objectMapper.nullNode();
        }
        try {
            if (value instanceof String text) {
                var trimmed = text.trim();
                return trimmed.isEmpty() ? objectMapper.nullNode() : objectMapper.readTree(trimmed);
            }
            return objectMapper.valueToTree(value);
        } catch (Exception ignored) {
            return objectMapper.nullNode();
        }
    }

    private LocalDate resolveEntryDate(
            InspectionReportStep dailyStep,
            InspectionReportStep basicInfoStep,
            OffsetDateTime fallback
    ) {
        var fromDaily = dateFromPayload(dailyStep == null ? null : dailyStep.payloadJson());
        if (fromDaily != null) {
            return fromDaily;
        }
        var fromBasicInfo = dateFromPayload(basicInfoStep == null ? null : basicInfoStep.payloadJson());
        if (fromBasicInfo != null) {
            return fromBasicInfo;
        }
        return fallback.toLocalDate();
    }

    private LocalDate dateFromPayload(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        for (var key : List.of("inspectionDate", "entryDate", "reportDate", "date")) {
            var value = payload.get(key);
            if (value == null) {
                continue;
            }
            try {
                return LocalDate.parse(value.toString());
            } catch (RuntimeException ignored) {
                // Keep scanning other supported date aliases.
            }
        }
        return null;
    }

    private List<Long> photoIds(JsonNode item) {
        var ids = item.path("photoIds");
        if (!ids.isArray()) {
            return List.of();
        }
        var result = new ArrayList<Long>();
        ids.forEach(node -> {
            if (node.canConvertToLong()) {
                result.add(node.asLong());
            } else if (node.isTextual()) {
                try {
                    result.add(Long.parseLong(node.asText()));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed client values.
                }
            }
        });
        return result.stream().filter(id -> id > 0).distinct().toList();
    }

    private SiteSupervisionEntryResponse toResponse(SiteSupervisionEntry entry) {
        return new SiteSupervisionEntryResponse(
                entry.id(),
                entry.officeId(),
                entry.projectId(),
                entry.siteId(),
                entry.entryDate(),
                entry.floorArea(),
                entry.tradeCode(),
                entry.tradeName(),
                entry.processCode(),
                entry.processName(),
                entry.itemCode(),
                entry.itemName(),
                entry.inspectionItemCode(),
                entry.inspectionItemName(),
                entry.supervisionContent(),
                entry.resultStatus(),
                entry.issueText(),
                entry.actionResult(),
                entry.photoIds(),
                entry.status(),
                entry.sourceType(),
                entry.sourceReportId(),
                entry.sourceReportRevision(),
                entry.sourceStepCode(),
                entry.sourceStepClientRevision(),
                entry.sourceEntryKey(),
                entry.catalogCode(),
                entry.catalogVersion(),
                entry.updatedAt());
    }

    private String text(JsonNode node, String key) {
        var value = node.path(key);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.asText("");
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
