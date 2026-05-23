package com.archdox.cloud.document.application;

import com.archdox.cloud.checklist.application.ChecklistService;
import com.archdox.cloud.configuration.application.ResolvedDocumentConfigPart;
import com.archdox.cloud.configuration.application.ResolvedDocumentConfiguration;
import com.archdox.cloud.configuration.application.ResolvedDocumentTemplateConfig;
import com.archdox.cloud.documenttype.application.DocumentTypeRegistryService;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.domain.InspectionReportStep;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.inspectiontarget.infra.InspectionReportTargetRepository;
import com.archdox.cloud.photo.domain.Photo;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.infra.PhotoAssetRepository;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.archdox.cloud.project.infra.ProjectRepository;
import com.archdox.cloud.site.infra.SiteRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DocumentSnapshotBuilder {
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
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("report", reportSnapshot(report));
        snapshot.put("configuration", configurationSnapshot(configuration));
        snapshot.put("project", projectSnapshot(report));
        snapshot.put("site", siteSnapshot(report));
        snapshot.put("documentType", documentTypeSnapshot(report));
        snapshot.put("steps", stepSnapshot(report));
        snapshot.put("targets", targetSnapshot(report));
        snapshot.put("checklistAnswers", checklistService.answerSnapshot(report));
        snapshot.put("photos", photoSnapshot(report));

        var templateFields = new LinkedHashMap<>(standardTemplateFieldResolver.resolve(snapshot));
        templateFields.putAll(templateBindingResolver.resolve(configuration.template().schema(), snapshot));
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

    private List<Map<String, Object>> photoSnapshot(InspectionReport report) {
        var photos = new ArrayList<Map<String, Object>>();
        for (Photo photo : photoRepository.findByOfficeIdAndReportIdOrderByIdDesc(report.officeId(), report.id())) {
            var working = photoAssetRepository.findByPhotoIdAndAssetType(photo.id(), PhotoAssetType.WORKING);
            var thumbnail = photoAssetRepository.findByPhotoIdAndAssetType(photo.id(), PhotoAssetType.THUMBNAIL);
            photos.add(Map.of(
                    "photoId", photo.id(),
                    "stepCode", photo.stepCode() == null ? "" : photo.stepCode(),
                    "checklistItemId", photo.checklistItemId() == null ? "" : photo.checklistItemId(),
                    "workingStorageRef", working.map(com.archdox.cloud.photo.domain.PhotoAsset::storageRef).orElse(""),
                    "thumbnailStorageRef", thumbnail.map(com.archdox.cloud.photo.domain.PhotoAsset::storageRef).orElse(""),
                    "width", photo.width() == null ? "" : photo.width(),
                    "height", photo.height() == null ? "" : photo.height(),
                    "hashSha256", photo.hashSha256()));
        }
        return photos;
    }
}
