package com.archdox.cloud.document.application;

import com.archdox.cloud.checklist.application.ChecklistService;
import com.archdox.cloud.configuration.application.ResolvedDocumentConfigPart;
import com.archdox.cloud.configuration.application.ResolvedDocumentConfiguration;
import com.archdox.cloud.configuration.application.ResolvedDocumentTemplateConfig;
import com.archdox.cloud.documenttype.application.DocumentTypeRegistryService;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.inspection.application.DailySupervisionContentFormatter;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.domain.InspectionReportStep;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.inspectiontarget.infra.InspectionReportTargetRepository;
import com.archdox.cloud.photo.domain.Photo;
import com.archdox.cloud.photo.domain.PhotoAssetStatus;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.infra.PhotoAssetRepository;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.archdox.cloud.project.infra.ProjectRepository;
import com.archdox.cloud.site.infra.SiteRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DocumentSnapshotBuilder {
    private static final int MAX_RENDER_OVERRIDE_COUNT = 30;
    private static final int MAX_RENDER_OVERRIDE_VALUE_LENGTH = 4_000;
    private static final Pattern REMARKS_TEXT_PATH = Pattern.compile(
            "^steps\\.REMARKS\\.payload\\.(specialNotes|remarks|issueAndAction|nextAction)$");
    private static final Pattern DAILY_LOG_TEXT_PATH = Pattern.compile(
            "^steps\\.DAILY_LOG\\.payload\\.(specialNotes|issueAndAction|issueAndActionResult|nextAction)$");
    private static final Pattern DAILY_LOG_ENTRY_DOCUMENT_NARRATIVE_PATH = Pattern.compile(
            "^steps\\.DAILY_LOG\\.payload\\.dailyItems\\.groups\\[(\\d+)]\\.entries\\[(\\d+)]\\.documentNarrativeText$");
    private final InspectionReportStepRepository stepRepository;
    private final PhotoRepository photoRepository;
    private final PhotoAssetRepository photoAssetRepository;
    private final InspectionReportTargetRepository reportTargetRepository;
    private final ChecklistService checklistService;
    private final ProjectRepository projectRepository;
    private final SiteRepository siteRepository;
    private final TemplateBindingResolver templateBindingResolver;
    private final OutputLayoutCompiler outputLayoutCompiler;
    private final StandardTemplateFieldResolver standardTemplateFieldResolver;
    private final DocumentTypeRegistryService documentTypeRegistryService;

    public DocumentSnapshotBuilder(
            InspectionReportStepRepository stepRepository,
            PhotoRepository photoRepository,
            PhotoAssetRepository photoAssetRepository,
            InspectionReportTargetRepository reportTargetRepository,
            ChecklistService checklistService,
            ProjectRepository projectRepository,
            SiteRepository siteRepository,
            TemplateBindingResolver templateBindingResolver,
            OutputLayoutCompiler outputLayoutCompiler,
            StandardTemplateFieldResolver standardTemplateFieldResolver,
            DocumentTypeRegistryService documentTypeRegistryService
    ) {
        this.stepRepository = stepRepository;
        this.photoRepository = photoRepository;
        this.photoAssetRepository = photoAssetRepository;
        this.reportTargetRepository = reportTargetRepository;
        this.checklistService = checklistService;
        this.projectRepository = projectRepository;
        this.siteRepository = siteRepository;
        this.templateBindingResolver = templateBindingResolver;
        this.outputLayoutCompiler = outputLayoutCompiler;
        this.standardTemplateFieldResolver = standardTemplateFieldResolver;
        this.documentTypeRegistryService = documentTypeRegistryService;
    }

    public Map<String, Object> build(
            InspectionReport report,
            ResolvedDocumentConfiguration configuration
    ) {
        return build(report, configuration, List.of());
    }

    public Map<String, Object> build(
            InspectionReport report,
            ResolvedDocumentConfiguration configuration,
            List<RenderOverride> renderOverrides
    ) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("report", reportSnapshot(report));
        snapshot.put("configuration", configurationSnapshot(configuration));
        snapshot.put("project", projectSnapshot(report));
        snapshot.put("site", siteSnapshot(report));
        snapshot.put("documentType", documentTypeSnapshot(report));
        var steps = stepSnapshot(report);
        snapshot.put("steps", steps);
        snapshot.put("targets", targetSnapshot(report));
        var appliedRenderOverrides = applyRenderOverrides(snapshot, renderOverrides);
        var photos = photoSnapshot(report, steps);
        var checklistAnswers = checklistAnswerSnapshot(report, photos);
        snapshot.put("checklistAnswers", checklistAnswers);
        snapshot.put("photos", photos);
        snapshot.put("checklistPhotos", checklistPhotoSnapshot(photos));
        if (!appliedRenderOverrides.isEmpty()) {
            snapshot.put("renderOverrides", appliedRenderOverrides);
        }

        var protectedTemplateFields = protectedTemplateFields(snapshot, appliedRenderOverrides);
        var templateFields = new LinkedHashMap<>(standardTemplateFieldResolver.resolve(snapshot));
        templateBindingResolver.resolve(configuration.template().schema(), snapshot)
                .forEach((key, value) -> mergeTemplateField(templateFields, protectedTemplateFields, key, value));
        var layoutBinding = outputLayoutCompiler.compile(configuration.outputLayout().payload(), snapshot);
        if (layoutBinding.sections().isEmpty()) {
            layoutBinding = documentTypeRegistryService.resolveByReportType(report.officeId(), report.reportType())
                    .map(definition -> outputLayoutCompiler.compile(definition.outputLayoutJson(), snapshot))
                    .orElse(layoutBinding);
        }
        snapshot.put("layoutSections", layoutBinding.sections());
        layoutBinding.templateFields().forEach(templateFields::putIfAbsent);
        snapshot.put("templateFields", templateFields);
        return snapshot;
    }

    private List<Map<String, Object>> applyRenderOverrides(
            Map<String, Object> snapshot,
            List<RenderOverride> renderOverrides
    ) {
        if (renderOverrides == null || renderOverrides.isEmpty()) {
            return List.of();
        }
        if (renderOverrides.size() > MAX_RENDER_OVERRIDE_COUNT) {
            throw new BadRequestException(
                    "DOCUMENT_RENDER_OVERRIDE_LIMIT_EXCEEDED",
                    "errors.document.renderOverrideLimitExceeded",
                    "Document render overrides exceed the allowed limit.",
                    Map.of("limit", MAX_RENDER_OVERRIDE_COUNT));
        }
        var applied = new ArrayList<Map<String, Object>>();
        for (RenderOverride override : renderOverrides) {
            if (override == null) {
                continue;
            }
            var path = stringValue(override.path()).trim();
            var value = stringValue(override.value()).trim();
            if (path.isBlank()) {
                continue;
            }
            if (value.length() > MAX_RENDER_OVERRIDE_VALUE_LENGTH) {
                throw new BadRequestException(
                        "DOCUMENT_RENDER_OVERRIDE_VALUE_TOO_LONG",
                        "errors.document.renderOverrideValueTooLong",
                        "Document render override value is too long.",
                        Map.of("path", path, "limit", MAX_RENDER_OVERRIDE_VALUE_LENGTH));
            }
            if (!applyRenderOverride(snapshot, path, value)) {
                throw new BadRequestException(
                        "DOCUMENT_RENDER_OVERRIDE_UNSUPPORTED_PATH",
                        "errors.document.renderOverrideUnsupportedPath",
                        "Document render override path is not supported.",
                        Map.of("path", path));
            }
            var entry = new LinkedHashMap<String, Object>();
            entry.put("path", path);
            entry.put("label", stringValue(override.label()).trim());
            entry.put("source", stringValue(override.source()).trim());
            applied.add(entry);
        }
        return applied;
    }

    private Set<String> protectedTemplateFields(
            Map<String, Object> snapshot,
            List<Map<String, Object>> appliedRenderOverrides
    ) {
        var fields = new LinkedHashSet<String>();
        if (hasDailyLogDocumentNarrativeText(snapshot)) {
            fields.add("supervisionContent");
        }
        for (Map<String, Object> override : appliedRenderOverrides) {
            var path = stringValue(override.get("path"));
            if (path.endsWith(".specialNotes")) {
                fields.add("specialNotes");
            } else if (path.endsWith(".issueAndAction") || path.endsWith(".issueAndActionResult")) {
                fields.add("issueAndAction");
                fields.add("correctionResults");
            } else if (path.endsWith(".nextAction")) {
                fields.add("nextAction");
            }
        }
        return fields;
    }

    private void mergeTemplateField(
            Map<String, Object> templateFields,
            Set<String> protectedTemplateFields,
            String key,
            Object value
    ) {
        if (protectedTemplateFields.contains(key) && !stringValue(templateFields.get(key)).isBlank()) {
            return;
        }
        templateFields.put(key, value);
    }

    private boolean hasDailyLogDocumentNarrativeText(Map<String, Object> snapshot) {
        var steps = mapValue(snapshot.get("steps"));
        var dailyLog = mapValue(steps.get("DAILY_LOG"));
        var payload = mapValue(dailyLog.get("payload"));
        var dailyItems = mapValue(payload.get("dailyItems"));
        for (Object groupValue : listValue(dailyItems.get("groups"))) {
            var group = mapValue(groupValue);
            for (Object entryValue : listValue(group.get("entries"))) {
                var entry = mapValue(entryValue);
                if (!stringValue(entry.get("documentNarrativeText")).isBlank()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean applyRenderOverride(Map<String, Object> snapshot, String path, String value) {
        var remarksMatcher = REMARKS_TEXT_PATH.matcher(path);
        if (remarksMatcher.matches()) {
            applyRemarksTextOverride(snapshot, remarksMatcher.group(1), value);
            return true;
        }
        var dailyLogMatcher = DAILY_LOG_TEXT_PATH.matcher(path);
        if (dailyLogMatcher.matches()) {
            applyDailyLogTextOverride(snapshot, dailyLogMatcher.group(1), value);
            return true;
        }
        var dailyEntryMatcher = DAILY_LOG_ENTRY_DOCUMENT_NARRATIVE_PATH.matcher(path);
        if (dailyEntryMatcher.matches()) {
            return applyDailyLogEntryDocumentNarrativeOverride(
                    snapshot,
                    Integer.parseInt(dailyEntryMatcher.group(1)),
                    Integer.parseInt(dailyEntryMatcher.group(2)),
                    value);
        }
        return false;
    }

    private void applyRemarksTextOverride(Map<String, Object> snapshot, String key, String value) {
        var payload = mutableStepPayload(snapshot, "REMARKS", true);
        payload.put(key, value);
    }

    private void applyDailyLogTextOverride(Map<String, Object> snapshot, String key, String value) {
        var payload = mutableStepPayload(snapshot, "DAILY_LOG", false);
        payload.put(key, value);
    }

    private boolean applyDailyLogEntryDocumentNarrativeOverride(
            Map<String, Object> snapshot,
            int groupIndex,
            int entryIndex,
            String value
    ) {
        var payload = mutableStepPayload(snapshot, "DAILY_LOG", false);
        var dailyItems = mutableChildMap(payload, "dailyItems");
        var groups = mutableChildList(dailyItems, "groups");
        if (groupIndex < 0 || groupIndex >= groups.size()) {
            return false;
        }
        var group = mutableMap(groups.get(groupIndex));
        groups.set(groupIndex, group);
        var entries = mutableChildList(group, "entries");
        if (entryIndex < 0 || entryIndex >= entries.size()) {
            return false;
        }
        var entry = mutableMap(entries.get(entryIndex));
        entry.put("documentNarrativeText", value);
        entries.set(entryIndex, entry);
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mutableStepPayload(
            Map<String, Object> snapshot,
            String stepCode,
            boolean createIfMissing
    ) {
        var steps = mutableMap(snapshot.get("steps"));
        snapshot.put("steps", steps);
        var step = mutableMap(steps.get(stepCode));
        if (step.isEmpty() && !createIfMissing) {
            throw new BadRequestException(
                    "DOCUMENT_RENDER_OVERRIDE_TARGET_NOT_FOUND",
                    "errors.document.renderOverrideTargetNotFound",
                    "Document render override step target was not found.",
                    Map.of("stepCode", stepCode));
        }
        if (step.isEmpty()) {
            step.put("payloadStorageMode", "INLINE_JSON");
            step.put("clientRevision", "");
            step.put("savedAt", "");
        }
        steps.put(stepCode, step);
        var payload = mutableMap(step.get("payload"));
        step.put("payload", payload);
        return payload;
    }

    private Map<String, Object> mutableChildMap(Map<String, Object> parent, String key) {
        var child = mutableMap(parent.get(key));
        parent.put(key, child);
        return child;
    }

    private List<Object> mutableChildList(Map<String, Object> parent, String key) {
        var child = mutableList(parent.get(key));
        parent.put(key, child);
        return child;
    }

    private Map<String, Object> mutableMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return new LinkedHashMap<>();
        }
        var mapped = new LinkedHashMap<String, Object>();
        rawMap.forEach((key, mapValue) -> mapped.put(String.valueOf(key), mapValue));
        return mapped;
    }

    private List<Object> mutableList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(rawList);
    }

    private Map<String, Object> reportSnapshot(InspectionReport report) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("id", report.id());
        snapshot.put("officeId", report.officeId());
        snapshot.put("projectId", report.projectId());
        snapshot.put("contentRevision", report.contentRevision());
        snapshot.put("submittedRevision", report.submittedRevision() == null ? "" : report.submittedRevision());
        snapshot.put("generatedRevision", report.generatedRevision() == null ? "" : report.generatedRevision());
        snapshot.put("siteId", report.siteId() == null ? "" : report.siteId());
        snapshot.put("reportNo", report.reportNo());
        snapshot.put("reportType", report.reportType());
        snapshot.put("title", report.title() == null ? "" : report.title());
        snapshot.put("status", report.status().name());
        snapshot.put("templateId", report.templateId() == null ? "" : report.templateId());
        return snapshot;
    }

    private Map<String, Object> projectSnapshot(InspectionReport report) {
        return projectRepository.findByIdAndOfficeId(report.projectId(), report.officeId())
                .map(project -> {
                    var snapshot = new LinkedHashMap<String, Object>();
                    snapshot.put("id", project.id());
                    snapshot.put("officeId", project.officeId());
                    snapshot.put("name", project.name());
                    snapshot.put("address", project.address() == null ? "" : project.address());
                    snapshot.put("buildingType", project.buildingType() == null ? "" : project.buildingType());
                    snapshot.put("startDate", project.startDate() == null ? "" : project.startDate().toString());
                    snapshot.put("endDate", project.endDate() == null ? "" : project.endDate().toString());
                    snapshot.put("status", project.status().name());
                    return Map.<String, Object>copyOf(snapshot);
                })
                .orElseGet(() -> Map.of("id", report.projectId()));
    }

    private Map<String, Object> siteSnapshot(InspectionReport report) {
        if (report.siteId() == null) {
            return Map.of();
        }
        return siteRepository.findByIdAndOfficeId(report.siteId(), report.officeId())
                .map(site -> {
                    var snapshot = new LinkedHashMap<String, Object>();
                    snapshot.put("id", site.id());
                    snapshot.put("officeId", site.officeId());
                    snapshot.put("projectId", site.projectId());
                    snapshot.put("siteCode", site.siteCode() == null ? "" : site.siteCode());
                    snapshot.put("name", site.name());
                    snapshot.put("address", site.address() == null ? "" : site.address());
                    snapshot.put("siteType", site.siteType() == null ? "" : site.siteType());
                    snapshot.put("startDate", site.startDate() == null ? "" : site.startDate().toString());
                    snapshot.put("endDate", site.endDate() == null ? "" : site.endDate().toString());
                    snapshot.put("status", site.status().name());
                    return Map.<String, Object>copyOf(snapshot);
                })
                .orElseGet(() -> Map.of("id", report.siteId()));
    }

    private Map<String, Object> documentTypeSnapshot(InspectionReport report) {
        return documentTypeRegistryService.resolveByReportType(report.officeId(), report.reportType())
                .map(definition -> {
                    var snapshot = new LinkedHashMap<String, Object>();
                    snapshot.put("id", definition.id());
                    snapshot.put("officeId", definition.officeId() == null ? "" : definition.officeId());
                    snapshot.put("code", definition.code());
                    snapshot.put("reportType", definition.reportType());
                    snapshot.put("name", definition.name());
                    snapshot.put("description", definition.description() == null ? "" : definition.description());
                    snapshot.put("category", definition.category());
                    snapshot.put("defaultTemplateCode", definition.defaultTemplateCode() == null ? "" : definition.defaultTemplateCode());
                    snapshot.put("defaultTemplateStorageRef", definition.defaultTemplateStorageRef() == null ? "" : definition.defaultTemplateStorageRef());
                    snapshot.put("checklistSchemaCode", definition.checklistSchemaCode() == null ? "" : definition.checklistSchemaCode());
                    snapshot.put("defaultOutputFormat", definition.defaultOutputFormat());
                    return Map.<String, Object>copyOf(snapshot);
                })
                .orElseGet(Map::of);
    }

    private Map<String, Object> configurationSnapshot(ResolvedDocumentConfiguration configuration) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("officeId", configuration.officeId());
        snapshot.put("reportType", configuration.reportType());
        snapshot.put("template", templateSnapshot(configuration.template()));
        snapshot.put("workflow", configPartSnapshot(configuration.workflow()));
        snapshot.put("ruleSet", configPartSnapshot(configuration.ruleSet()));
        snapshot.put("outputLayout", configPartSnapshot(configuration.outputLayout()));
        return snapshot;
    }

    private Map<String, Object> templateSnapshot(ResolvedDocumentTemplateConfig template) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("source", template.source().name());
        snapshot.put("definitionId", template.definitionId());
        snapshot.put("revisionId", template.revisionId());
        snapshot.put("code", template.code());
        snapshot.put("name", template.name());
        snapshot.put("reportType", template.reportType());
        snapshot.put("version", template.version());
        snapshot.put("storageKind", template.templateStorageKind());
        snapshot.put("storageRef", template.templateStorageRef());
        snapshot.put("schema", template.schema());
        snapshot.put("composePolicy", template.composePolicy());
        snapshot.put("aiPrompts", template.aiPrompts());
        return snapshot;
    }

    private Map<String, Object> configPartSnapshot(ResolvedDocumentConfigPart part) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("source", part.source().name());
        snapshot.put("definitionId", part.definitionId());
        snapshot.put("revisionId", part.revisionId());
        snapshot.put("code", part.code());
        snapshot.put("name", part.name());
        snapshot.put("reportType", part.reportType());
        snapshot.put("version", part.version());
        snapshot.put("payload", part.payload());
        return snapshot;
    }

    private Map<String, Object> stepSnapshot(InspectionReport report) {
        var steps = new LinkedHashMap<String, Object>();
        for (InspectionReportStep step : stepRepository.findByReportIdOrderById(report.id())) {
            steps.put(step.stepCode(), Map.of(
                    "payloadStorageMode", step.payloadStorageMode().name(),
                    "payload", step.payloadJson() == null ? Map.of() : step.payloadJson(),
                    "clientRevision", step.clientRevision(),
                    "savedAt", step.savedAt().toString()));
        }
        return steps;
    }

    private List<Map<String, Object>> targetSnapshot(InspectionReport report) {
        return reportTargetRepository.findByOfficeIdAndReportIdOrderByRoleAscIdAsc(report.officeId(), report.id()).stream()
                .map(target -> Map.<String, Object>of(
                        "reportTargetId", target.id(),
                        "targetId", target.targetId(),
                        "role", target.role().name(),
                        "snapshot", target.snapshotJson(),
                        "createdAt", target.createdAt().toString()))
                .toList();
    }

    private List<Map<String, Object>> checklistAnswerSnapshot(
            InspectionReport report,
            List<Map<String, Object>> photos
    ) {
        var photosByChecklistItemId = photosByChecklistItemId(photos);
        var answers = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> answer : checklistService.answerSnapshot(report)) {
            var enriched = new LinkedHashMap<>(answer);
            var linkedPhotos = photosByChecklistItemId.getOrDefault(longValue(answer.get("checklistItemId")), List.of());
            enriched.put("photoCount", linkedPhotos.size());
            enriched.put("photoIds", linkedPhotos.stream().map(photo -> photo.get("photoId")).toList());
            enriched.put("photos", linkedPhotos);
            answers.add(enriched);
        }
        return answers;
    }

    private List<Map<String, Object>> photoSnapshot(InspectionReport report, Map<String, Object> steps) {
        var rawPhotos = photoRepository.findByOfficeIdAndReportIdAndStatusNotOrderByIdDesc(
                report.officeId(),
                report.id(),
                PhotoStatus.DELETED);
        var checklistItemSnapshots = rawPhotos.stream().anyMatch(photo -> photo.checklistItemId() != null)
                ? checklistService.itemSnapshotById(report)
                : Map.<Long, Map<String, Object>>of();
        var dailyPhotoContexts = dailyPhotoContexts(steps);
        var photos = new ArrayList<Map<String, Object>>();
        for (Photo photo : rawPhotos) {
            var working = photoAssetRepository.findByPhotoIdAndAssetType(photo.id(), PhotoAssetType.WORKING);
            var thumbnail = photoAssetRepository.findByPhotoIdAndAssetType(photo.id(), PhotoAssetType.THUMBNAIL);
            var uploadedWorking = working.filter(asset -> asset.status() == PhotoAssetStatus.UPLOADED);
            var uploadedThumbnail = thumbnail.filter(asset -> asset.status() == PhotoAssetStatus.UPLOADED);
            var checklistItem = photo.checklistItemId() == null ? Map.<String, Object>of() : checklistItemSnapshots.getOrDefault(photo.checklistItemId(), Map.of());
            var dailyContext = dailyPhotoContexts.getOrDefault(photo.id(), Map.of());
            var snapshot = new LinkedHashMap<String, Object>();
            snapshot.put("photoId", photo.id());
            snapshot.put("stepCode", photo.stepCode() == null ? "" : photo.stepCode());
            snapshot.put("checklistItemId", photo.checklistItemId() == null ? "" : photo.checklistItemId());
            snapshot.put("checklistItemCode", stringValue(checklistItem.get("itemCode")));
            snapshot.put("checklistItemLabel", stringValue(checklistItem.get("label")));
            snapshot.put("checklistLinked", photo.checklistItemId() != null);
            snapshot.put("dailyLinked", !dailyContext.isEmpty());
            snapshot.putAll(dailyContext);
            snapshot.put("caption", photoCaption(photo, checklistItem, dailyContext));
            snapshot.put("workingStorageRef", uploadedWorking.map(com.archdox.cloud.photo.domain.PhotoAsset::storageRef).orElse(""));
            snapshot.put("thumbnailStorageRef", uploadedThumbnail.map(com.archdox.cloud.photo.domain.PhotoAsset::storageRef).orElse(""));
            snapshot.put("workingReady", uploadedWorking.isPresent());
            snapshot.put("thumbnailReady", uploadedThumbnail.isPresent());
            snapshot.put("mimeType", uploadedWorking.map(com.archdox.cloud.photo.domain.PhotoAsset::mimeType).orElse(photo.mimeType()));
            snapshot.put("width", photo.width() == null ? "" : photo.width());
            snapshot.put("height", photo.height() == null ? "" : photo.height());
            snapshot.put("hashSha256", photo.hashSha256());
            photos.add(snapshot);
        }
        return photos;
    }

    private Map<Long, Map<String, Object>> dailyPhotoContexts(Map<String, Object> steps) {
        var contexts = new LinkedHashMap<Long, Map<String, Object>>();
        var dailyLog = mapValue(steps.get("DAILY_LOG"));
        var payload = mapValue(dailyLog.get("payload"));
        var dailyItems = mapValue(payload.get("dailyItems"));
        for (Object groupValue : listValue(dailyItems.get("groups"))) {
            var group = mapValue(groupValue);
            var tradeName = stringValue(group.get("tradeName"));
            var phaseName = stringValue(group.get("phaseName"));
            var rootName = tradeName.isBlank() ? phaseName : tradeName;
            var processName = stringValue(group.get("processName"));
            var floor = stringValue(group.get("floor"));
            var groupLabel = joinNonBlank(rootName, processName, floor);
            for (Object entryValue : listValue(group.get("entries"))) {
                var entry = mapValue(entryValue);
                var itemName = stringValue(entry.get("inspectionItemName"));
                for (Object rowValue : listValue(entry.get("checklistRows"))) {
                    var row = mapValue(rowValue);
                    var rowLabel = stringValue(row.get("label"));
                    var rowDetail = DailySupervisionContentFormatter.formatRowContent(row);
                    for (Object photoIdValue : listValue(row.get("photoIds"))) {
                        var photoId = longValue(photoIdValue);
                        if (photoId == null) {
                            continue;
                        }
                        var context = new LinkedHashMap<String, Object>();
                        context.put("dailyGroupLabel", groupLabel);
                        context.put("tradeName", tradeName);
                        context.put("phaseName", phaseName);
                        context.put("processName", processName);
                        context.put("floor", floor);
                        context.put("inspectionItemName", itemName);
                        context.put("checklistRowLabel", rowLabel);
                        context.put("supervisionContent", rowDetail);
                        context.put("dailyCaption", joinCaption(groupLabel, itemName, rowLabel));
                        contexts.put(photoId, context);
                    }
                }
            }
        }
        return contexts;
    }

    private Map<Long, List<Map<String, Object>>> photosByChecklistItemId(List<Map<String, Object>> photos) {
        var grouped = new LinkedHashMap<Long, List<Map<String, Object>>>();
        for (Map<String, Object> photo : photos) {
            var checklistItemId = longValue(photo.get("checklistItemId"));
            if (checklistItemId == null) {
                continue;
            }
            grouped.computeIfAbsent(checklistItemId, ignored -> new ArrayList<>()).add(photo);
        }
        return grouped;
    }

    private List<Map<String, Object>> checklistPhotoSnapshot(List<Map<String, Object>> photos) {
        var grouped = photosByChecklistItemId(photos);
        var checklistPhotos = new ArrayList<Map<String, Object>>();
        for (Map.Entry<Long, List<Map<String, Object>>> entry : grouped.entrySet()) {
            var firstPhoto = entry.getValue().isEmpty() ? Map.<String, Object>of() : entry.getValue().get(0);
            var snapshot = new LinkedHashMap<String, Object>();
            snapshot.put("checklistItemId", entry.getKey());
            snapshot.put("itemCode", stringValue(firstPhoto.get("checklistItemCode")));
            snapshot.put("label", stringValue(firstPhoto.get("checklistItemLabel")));
            snapshot.put("photoCount", entry.getValue().size());
            snapshot.put("photoIds", entry.getValue().stream().map(photo -> photo.get("photoId")).filter(Objects::nonNull).toList());
            snapshot.put("photos", entry.getValue());
            checklistPhotos.add(snapshot);
        }
        return checklistPhotos;
    }

    private String checklistPhotoCaption(Photo photo, Map<String, Object> checklistItem) {
        var label = stringValue(checklistItem.get("label"));
        if (!label.isBlank()) {
            return label;
        }
        if (photo.stepCode() != null && !photo.stepCode().isBlank()) {
            return photo.stepCode();
        }
        return "Photo " + photo.id();
    }

    private String photoCaption(Photo photo, Map<String, Object> checklistItem, Map<String, Object> dailyContext) {
        var dailyCaption = stringValue(dailyContext.get("dailyCaption"));
        if (!dailyCaption.isBlank()) {
            return dailyCaption;
        }
        return checklistPhotoCaption(photo, checklistItem);
    }

    private String joinCaption(String... values) {
        var result = new ArrayList<String>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value);
            }
        }
        return String.join(" - ", result);
    }

    private String joinNonBlank(String... values) {
        var result = new ArrayList<String>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value);
            }
        }
        return String.join(" / ", result);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Object>();
        raw.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record RenderOverride(
            String path,
            String value,
            String label,
            String source
    ) {
    }
}
