package com.archdox.cloud.document.application;

import com.archdox.cloud.document.domain.DocumentArtifact;
import com.archdox.cloud.document.domain.DocumentArtifactStorageKind;
import com.archdox.cloud.document.domain.DocumentArtifactType;
import com.archdox.cloud.document.domain.DocumentJob;
import com.archdox.cloud.document.domain.DocumentJobProgressStep;
import com.archdox.cloud.document.domain.DocumentJobStatus;
import com.archdox.cloud.document.domain.DocumentWorkerType;
import com.archdox.cloud.document.dto.CreateDocumentJobRequest;
import com.archdox.cloud.document.dto.DocumentArtifactResponse;
import com.archdox.cloud.document.dto.DocumentJobResponse;
import com.archdox.cloud.document.event.DocumentGeneratedEvent;
import com.archdox.cloud.document.infra.DocumentArtifactRepository;
import com.archdox.cloud.document.infra.DocumentJobRepository;
import com.archdox.cloud.document.infra.DocumentLocalObjectStore;
import com.archdox.cloud.checklist.application.ChecklistService;
import com.archdox.cloud.configuration.application.ConfigurationRegistryService;
import com.archdox.cloud.configuration.application.ResolvedDocumentConfigPart;
import com.archdox.cloud.configuration.application.ResolvedDocumentConfiguration;
import com.archdox.cloud.configuration.application.ResolvedDocumentTemplateConfig;
import com.archdox.cloud.configuration.domain.ConfigResolutionSource;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspection.application.InspectionReportService;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.domain.InspectionReportStep;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.inspectiontarget.infra.InspectionReportTargetRepository;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.photo.domain.Photo;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.infra.PhotoAssetRepository;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.archdox.cloud.project.infra.ProjectRepository;
import com.archdox.cloud.site.infra.SiteRepository;
import com.archdox.document.ArtifactType;
import com.archdox.document.DocumentEngine;
import com.archdox.document.DocumentGenerationRequest;
import com.archdox.document.GenerationStatus;
import com.archdox.document.OutputFormat;
import com.archdox.document.PhotoLayoutSize;
import com.archdox.document.TemplateSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.bloom.EventBus;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DocumentJobService {
    private final InspectionReportService inspectionReportService;
    private final InspectionReportRepository reportRepository;
    private final InspectionReportStepRepository stepRepository;
    private final PhotoRepository photoRepository;
    private final PhotoAssetRepository photoAssetRepository;
    private final DocumentJobRepository documentJobRepository;
    private final DocumentArtifactRepository documentArtifactRepository;
    private final DocumentLocalObjectStore objectStore;
    private final DocumentEngine documentEngine;
    private final EventBus eventBus;
    private final OperationEventService operationEventService;
    private final ConfigurationRegistryService configurationRegistryService;
    private final InspectionReportTargetRepository reportTargetRepository;
    private final ChecklistService checklistService;
    private final ProjectRepository projectRepository;
    private final SiteRepository siteRepository;
    private final ObjectMapper objectMapper;

    public DocumentJobService(
            InspectionReportService inspectionReportService,
            InspectionReportRepository reportRepository,
            InspectionReportStepRepository stepRepository,
            PhotoRepository photoRepository,
            PhotoAssetRepository photoAssetRepository,
            DocumentJobRepository documentJobRepository,
            DocumentArtifactRepository documentArtifactRepository,
            DocumentLocalObjectStore objectStore,
            DocumentEngine documentEngine,
            EventBus eventBus,
            OperationEventService operationEventService,
            ConfigurationRegistryService configurationRegistryService,
            InspectionReportTargetRepository reportTargetRepository,
            ChecklistService checklistService,
            ProjectRepository projectRepository,
            SiteRepository siteRepository,
            ObjectMapper objectMapper
    ) {
        this.inspectionReportService = inspectionReportService;
        this.reportRepository = reportRepository;
        this.stepRepository = stepRepository;
        this.photoRepository = photoRepository;
        this.photoAssetRepository = photoAssetRepository;
        this.documentJobRepository = documentJobRepository;
        this.documentArtifactRepository = documentArtifactRepository;
        this.objectStore = objectStore;
        this.documentEngine = documentEngine;
        this.eventBus = eventBus;
        this.operationEventService = operationEventService;
        this.configurationRegistryService = configurationRegistryService;
        this.reportTargetRepository = reportTargetRepository;
        this.checklistService = checklistService;
        this.projectRepository = projectRepository;
        this.siteRepository = siteRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DocumentJobResponse create(Long reportId, CreateDocumentJobRequest request, UserPrincipal principal) {
        var report = inspectionReportService.requireReport(reportId);
        requireCanRequestGeneration(report);
        var outputFormat = request.normalizedOutputFormat();
        var workerType = request.normalizedWorkerType();
        var officeId = OfficeContext.requireCurrentOfficeId();
        var now = OffsetDateTime.now();
        var configuration = configurationRegistryService.resolveForDocumentGeneration(officeId, report.reportType());
        var snapshot = buildInputSnapshot(report, configuration);
        var reportRevision = report.generationRevision();
        var job = documentJobRepository.saveAndFlush(new DocumentJob(
                officeId,
                report.id(),
                report.projectId(),
                reportRevision,
                selectedTemplateRevisionId(report, configuration),
                principal.userId(),
                workerType,
                outputFormat,
                snapshot,
                now));
        report.requestGeneration(job.id(), reportRevision, now);
        operationEventService.record(
                officeId,
                OperationEventSeverity.INFO,
                "DOCUMENT_JOB_REQUESTED",
                "document-generation",
                "document-job:" + job.id(),
                "DOCUMENT_JOB",
                job.id(),
                principal.userId(),
                null,
                "Document generation workflow requested.",
                Map.of(
                        "reportId", report.id(),
                        "reportRevision", reportRevision,
                        "workerType", workerType.name(),
                        "outputFormat", outputFormat.name(),
                        "templateRevisionId", selectedTemplateRevisionId(report, configuration) == null
                                ? ""
                                : selectedTemplateRevisionId(report, configuration)));
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public List<DocumentJobResponse> listByReport(Long reportId) {
        var report = inspectionReportService.requireReport(reportId);
        return documentJobRepository.findByOfficeIdAndReportIdOrderByRequestedAtDesc(report.officeId(), report.id())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentJobResponse get(Long jobId) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        var job = documentJobRepository.findByIdAndOfficeId(jobId, officeId)
                .orElseThrow(() -> new NotFoundException("Document job not found"));
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public void validateJobReady(Long officeId, Long jobId) {
        var job = requireFlowJob(officeId, jobId);
        requireFlowReport(officeId, job.reportId());
        if (job.status() == DocumentJobStatus.GENERATED) {
            return;
        }
        if (job.status() != DocumentJobStatus.REQUESTED
                && job.status() != DocumentJobStatus.GENERATING
                && job.status() != DocumentJobStatus.FAILED) {
            throw new DocumentGenerationException(
                    "INVALID_DOCUMENT_JOB_STATUS",
                    "Document job cannot start generation in status " + job.status());
        }
    }

    @Transactional
    public void markProgress(
            Long officeId,
            Long jobId,
            DocumentJobProgressStep progressStep,
            int progressPercent,
            String progressMessage
    ) {
        var job = requireFlowJob(officeId, jobId);
        if (job.status() == DocumentJobStatus.GENERATED || job.status() == DocumentJobStatus.FAILED) {
            return;
        }
        job.updateProgress(progressStep, progressPercent, progressMessage, OffsetDateTime.now());
    }

    @Transactional(noRollbackFor = DocumentGenerationException.class)
    public void generateCloudDocument(Long officeId, Long jobId) {
        var job = requireFlowJob(officeId, jobId);
        var report = requireFlowReport(officeId, job.reportId());
        if (job.workerType() != DocumentWorkerType.CLOUD) {
            throw new DocumentGenerationException("UNSUPPORTED_WORKER_TYPE", "Document job is not routed to CLOUD");
        }
        if (job.status() == DocumentJobStatus.GENERATED) {
            return;
        }
        executeCloudGeneration(report, job);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildArchDoxAgentRenderPayload(Long officeId, Long jobId) {
        var job = requireFlowJob(officeId, jobId);
        var report = requireFlowReport(officeId, job.reportId());
        if (job.workerType() != DocumentWorkerType.ARCHDOX_AGENT) {
            throw new DocumentGenerationException("UNSUPPORTED_WORKER_TYPE", "Document job is not routed to ARCHDOX_AGENT");
        }
        var request = toEngineRequest(job, report);
        var payload = new LinkedHashMap<String, Object>();
        payload.put("documentJobId", job.id());
        payload.put("officeId", job.officeId());
        payload.put("reportId", job.reportId());
        payload.put("reportRevision", job.reportRevision());
        payload.put("projectId", job.projectId());
        payload.put("siteId", report.siteId());
        payload.put("outputFormat", request.outputFormat().name());
        payload.put("inputSnapshot", request.payload());
        payload.put("template", Map.of(
                "templateCode", request.template().templateCode(),
                "version", request.template().version(),
                "storageRef", request.template().storageRef(),
                "schemaJson", request.template().schemaJson(),
                "composePolicyJson", request.template().composePolicyJson()));
        payload.put("photos", request.photos().stream()
                .map(photo -> {
                    var photoPayload = new LinkedHashMap<String, Object>();
                    photoPayload.put("photoId", photo.photoId());
                    photoPayload.put("checklistItemKey", photo.checklistItemKey());
                    photoPayload.put("storageRef", photo.storageRef());
                    photoPayload.put("caption", photo.caption());
                    photoPayload.put("layoutSize", photo.layoutSize().name());
                    return photoPayload;
                })
                .toList());
        payload.put("resultStorageKind", DocumentArtifactStorageKind.ARCHDOX_AGENT.name());
        return payload;
    }

    @Transactional
    public void markArchDoxAgentRenderDispatched(Long officeId, Long jobId) {
        var job = requireFlowJob(officeId, jobId);
        var report = requireFlowReport(officeId, job.reportId());
        if (job.status() == DocumentJobStatus.GENERATED || job.status() == DocumentJobStatus.FAILED) {
            return;
        }
        var now = OffsetDateTime.now();
        job.markGenerating(now);
        report.markGenerating(now);
        job.updateProgress(
                DocumentJobProgressStep.DISPATCHING,
                25,
                "Dispatching document render command to ArchDox Agent.",
                now);
    }

    @Transactional
    public void markArchDoxAgentRenderAcked(Long officeId, Long jobId) {
        var job = requireFlowJob(officeId, jobId);
        if (job.status() == DocumentJobStatus.GENERATED || job.status() == DocumentJobStatus.FAILED) {
            return;
        }
        job.updateProgress(
                DocumentJobProgressStep.WAITING_FOR_AGENT,
                45,
                "ArchDox Agent accepted the document render command.",
                OffsetDateTime.now());
    }

    @Transactional
    public void completeArchDoxAgentDocument(Long officeId, Long jobId, Map<String, Object> result) {
        var job = requireFlowJob(officeId, jobId);
        var report = requireFlowReport(officeId, job.reportId());
        if (job.status() == DocumentJobStatus.GENERATED) {
            return;
        }
        var artifactsPayload = mapList(result == null ? null : result.get("artifacts"));
        if (artifactsPayload.isEmpty()) {
            markFailed(report, job, "DOCUMENT_ARTIFACT_MISSING", "ArchDox Agent did not return any document artifacts");
            throw new DocumentGenerationException("DOCUMENT_ARTIFACT_MISSING", "ArchDox Agent did not return any document artifacts");
        }
        job.updateProgress(
                DocumentJobProgressStep.STORING_ARTIFACTS,
                90,
                "Saving ArchDox Agent document artifact metadata.",
                OffsetDateTime.now());
        documentArtifactRepository.deleteByDocumentJobId(job.id());
        var artifacts = new ArrayList<DocumentArtifact>();
        for (var artifactPayload : artifactsPayload) {
            artifacts.add(documentArtifactRepository.save(new DocumentArtifact(
                    job.officeId(),
                    job.id(),
                    job.reportId(),
                    DocumentArtifactType.valueOf(requiredString(artifactPayload, "artifactType")),
                    DocumentArtifactStorageKind.valueOf(optionalString(
                            artifactPayload,
                            "storageKind",
                            DocumentArtifactStorageKind.ARCHDOX_AGENT.name())),
                    requiredString(artifactPayload, "storageRef"),
                    requiredString(artifactPayload, "fileName"),
                    optionalString(artifactPayload, "mimeType", "application/octet-stream"),
                    longValue(artifactPayload.getOrDefault("bytes", 0L)),
                    optionalString(artifactPayload, "hashSha256", ""),
                    OffsetDateTime.now())));
        }
        var completedAt = OffsetDateTime.now();
        job.markGenerated(completedAt);
        report.markGenerated(job.reportRevision(), completedAt);
        recordGenerated(job, "ArchDox Agent document generation completed.", artifacts.size());
        publishAfterCommit(new DocumentGeneratedEvent(
                job.officeId(),
                job.reportId(),
                job.id(),
                artifacts.stream().map(DocumentArtifact::id).toList(),
                completedAt));
    }

    @Transactional
    public void markGenerationFailed(Long officeId, Long jobId, String errorCode, String errorMessage) {
        var job = requireFlowJob(officeId, jobId);
        var report = requireFlowReport(officeId, job.reportId());
        if (job.status() == DocumentJobStatus.GENERATED || job.status() == DocumentJobStatus.FAILED) {
            return;
        }
        markFailed(report, job, errorCode, errorMessage);
    }

    private void executeCloudGeneration(InspectionReport report, DocumentJob job) {
        var startedAt = OffsetDateTime.now();
        job.markGenerating(startedAt);
        job.updateProgress(
                DocumentJobProgressStep.RENDERING,
                35,
                "문서 내용을 렌더링하는 중입니다.",
                startedAt);
        report.markGenerating(startedAt);

        try {
            var result = documentEngine.generate(toEngineRequest(job, report));
            if (result.status() != GenerationStatus.COMPLETED) {
                markFailed(report, job, result.errorCode(), result.errorMessage());
                throw new DocumentGenerationException(
                        result.errorCode() == null ? "DOCUMENT_GENERATION_FAILED" : result.errorCode(),
                        result.errorMessage() == null ? "Document generation failed" : result.errorMessage());
            }
            var artifacts = new ArrayList<DocumentArtifact>();
            documentArtifactRepository.deleteByDocumentJobId(job.id());
            job.updateProgress(
                    DocumentJobProgressStep.STORING_ARTIFACTS,
                    75,
                    "생성된 문서 파일을 저장하는 중입니다.",
                    OffsetDateTime.now());
            for (var generatedArtifact : result.artifacts()) {
                if (generatedArtifact.content() == null || generatedArtifact.content().length == 0) {
                    throw new IllegalStateException("Document engine returned artifact without content");
                }
                objectStore.write(generatedArtifact.storageRef(), generatedArtifact.content());
                artifacts.add(documentArtifactRepository.save(new DocumentArtifact(
                        job.officeId(),
                        job.id(),
                        job.reportId(),
                        toCloudArtifactType(generatedArtifact.type()),
                        DocumentArtifactStorageKind.API_LOCAL,
                        generatedArtifact.storageRef(),
                        generatedArtifact.fileName(),
                        mimeType(generatedArtifact.type()),
                        generatedArtifact.bytes(),
                        generatedArtifact.sha256(),
                        OffsetDateTime.now())));
            }
            var completedAt = OffsetDateTime.now();
            job.markGenerated(completedAt);
            report.markGenerated(job.reportRevision(), completedAt);
            recordGenerated(job, "Cloud document generation completed.", artifacts.size());
            publishAfterCommit(new DocumentGeneratedEvent(
                    job.officeId(),
                    job.reportId(),
                    job.id(),
                    artifacts.stream().map(DocumentArtifact::id).toList(),
                    completedAt));
        } catch (DocumentGenerationException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            markFailed(report, job, "DOCUMENT_GENERATION_FAILED", ex.getMessage());
            throw new DocumentGenerationException("DOCUMENT_GENERATION_FAILED", ex.getMessage(), ex);
        }
    }

    private void markFailed(InspectionReport report, DocumentJob job, String errorCode, String errorMessage) {
        var completedAt = OffsetDateTime.now();
        job.markFailed(errorCode == null ? "DOCUMENT_GENERATION_FAILED" : errorCode, errorMessage, completedAt);
        report.markGenerationFailed(completedAt);
        operationEventService.record(
                job.officeId(),
                OperationEventSeverity.ERROR,
                "DOCUMENT_JOB_FAILED",
                "document-generation",
                "document-job:" + job.id(),
                "DOCUMENT_JOB",
                job.id(),
                errorMessage == null || errorMessage.isBlank() ? "Document generation failed." : errorMessage,
                Map.of(
                        "reportId", job.reportId(),
                        "errorCode", errorCode == null ? "DOCUMENT_GENERATION_FAILED" : errorCode,
                        "workerType", job.workerType().name()));
    }

    private void recordGenerated(DocumentJob job, String message, int artifactCount) {
        operationEventService.record(
                job.officeId(),
                OperationEventSeverity.INFO,
                "DOCUMENT_JOB_GENERATED",
                "document-generation",
                "document-job:" + job.id(),
                "DOCUMENT_JOB",
                job.id(),
                message,
                Map.of(
                        "reportId", job.reportId(),
                        "workerType", job.workerType().name(),
                        "artifactCount", artifactCount));
    }

    private DocumentJob requireFlowJob(Long officeId, Long jobId) {
        return documentJobRepository.findByIdAndOfficeId(jobId, officeId)
                .orElseThrow(() -> new NotFoundException("Document job not found"));
    }

    private InspectionReport requireFlowReport(Long officeId, Long reportId) {
        return reportRepository.findByIdAndOfficeId(reportId, officeId)
                .orElseThrow(() -> new NotFoundException("Inspection report not found"));
    }

    private DocumentGenerationRequest toEngineRequest(DocumentJob job, InspectionReport report) {
        return new DocumentGenerationRequest(
                String.valueOf(job.id()),
                String.valueOf(job.officeId()),
                String.valueOf(report.id()),
                templateSpec(job, report),
                job.inputSnapshotJson(),
                toEnginePhotos(report),
                job.outputFormat());
    }

    private Map<String, Object> buildInputSnapshot(
            InspectionReport report,
            ResolvedDocumentConfiguration configuration
    ) {
        var snapshot = new LinkedHashMap<String, Object>();
        var reportSnapshot = new LinkedHashMap<String, Object>();
        reportSnapshot.put("id", report.id());
        reportSnapshot.put("officeId", report.officeId());
        reportSnapshot.put("projectId", report.projectId());
        reportSnapshot.put("contentRevision", report.contentRevision());
        reportSnapshot.put("submittedRevision", report.submittedRevision() == null ? "" : report.submittedRevision());
        reportSnapshot.put("generatedRevision", report.generatedRevision() == null ? "" : report.generatedRevision());
        reportSnapshot.put("siteId", report.siteId() == null ? "" : report.siteId());
        reportSnapshot.put("reportNo", report.reportNo());
        reportSnapshot.put("reportType", report.reportType());
        reportSnapshot.put("title", report.title() == null ? "" : report.title());
        reportSnapshot.put("status", report.status().name());
        reportSnapshot.put("templateId", report.templateId() == null ? "" : report.templateId());
        snapshot.put("report", reportSnapshot);
        snapshot.put("configuration", configurationSnapshot(configuration));
        snapshot.put("project", projectSnapshot(report));
        snapshot.put("site", siteSnapshot(report));
        snapshot.put("steps", stepSnapshot(report));
        snapshot.put("targets", targetSnapshot(report));
        snapshot.put("checklistAnswers", checklistService.answerSnapshot(report));
        snapshot.put("photos", photoSnapshot(report));
        snapshot.put("templateFields", templateFields(configuration.template().schema(), snapshot));
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

    private Map<String, Object> templateFields(Map<String, Object> schema, Map<String, Object> snapshot) {
        var fieldSources = templateFieldSources(schema);
        if (fieldSources.isEmpty()) {
            return Map.of();
        }
        var fields = new LinkedHashMap<String, Object>();
        fieldSources.forEach((fieldName, sourcePath) -> {
            var value = readPath(snapshot, sourcePath).orElse("");
            fields.put(fieldName, value == null ? "" : value);
        });
        return fields;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> templateFieldSources(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return Map.of();
        }
        var sources = new LinkedHashMap<String, String>();
        readFieldSourceMap(schema.get("bindings"), sources);
        readFieldSourceMap(schema.get("fieldMappings"), sources);
        readFieldSourceMap(schema.get("fields"), sources);
        if (schema.get("fields") instanceof List<?> fields) {
            for (Object field : fields) {
                if (field instanceof Map<?, ?> rawField) {
                    var name = firstString(rawField, "key", "name", "placeholder");
                    var source = firstString(rawField, "source", "path", "binding");
                    if (name != null && source != null) {
                        sources.put(name, source);
                    }
                }
            }
        }
        return sources;
    }

    private void readFieldSourceMap(Object value, Map<String, String> sources) {
        if (!(value instanceof Map<?, ?> mappings)) {
            return;
        }
        mappings.forEach((rawName, rawSource) -> {
            var name = stringValue(rawName);
            var source = fieldSource(rawSource);
            if (name != null && source != null) {
                sources.put(name, source);
            }
        });
    }

    private String fieldSource(Object value) {
        if (value instanceof Map<?, ?> map) {
            return firstString(map, "source", "path", "binding");
        }
        return stringValue(value);
    }

    private String firstString(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            var value = map.get(key);
            var text = stringValue(value);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private Optional<Object> readPath(Object root, String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        Object current = root;
        for (String segment : path.split("\\.")) {
            current = readSegment(current, segment);
            if (current == null) {
                return Optional.empty();
            }
        }
        return Optional.of(current);
    }

    private Object readSegment(Object current, String segment) {
        if (segment == null || segment.isBlank()) {
            return null;
        }
        var key = segment;
        Integer index = null;
        var bracketStart = segment.indexOf('[');
        if (bracketStart >= 0 && segment.endsWith("]")) {
            key = segment.substring(0, bracketStart);
            var rawIndex = segment.substring(bracketStart + 1, segment.length() - 1);
            try {
                index = Integer.parseInt(rawIndex);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        Object value = current;
        if (!key.isBlank()) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            value = map.get(key);
        }
        if (index == null) {
            return value;
        }
        if (value instanceof List<?> list && index >= 0 && index < list.size()) {
            return list.get(index);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private TemplateSpec templateSpec(DocumentJob job, InspectionReport report) {
        var configuration = mapValue(job.inputSnapshotJson().get("configuration"));
        var template = mapValue(configuration.get("template"));
        var source = stringValue(template.get("source"));
        if (!ConfigResolutionSource.NOT_CONFIGURED.name().equals(source) && template.get("revisionId") != null) {
            return new TemplateSpec(
                    optionalStringValue(template.get("code"), report.reportType()),
                    intValue(template.get("version"), 1),
                    optionalStringValue(
                            template.get("storageRef"),
                            report.templateId() == null
                                    ? "templates/default.docx"
                                    : "templates/" + report.templateId() + ".docx"),
                    jsonString(template.get("schema")),
                    jsonString(template.get("composePolicy")));
        }
        return new TemplateSpec(
                report.reportType(),
                1,
                report.templateId() == null ? "templates/default.docx" : "templates/" + report.templateId() + ".docx",
                "{}",
                "{}");
    }

    private Long selectedTemplateRevisionId(
            InspectionReport report,
            ResolvedDocumentConfiguration configuration
    ) {
        if (configuration.template().source() == ConfigResolutionSource.NOT_CONFIGURED) {
            return report.templateId();
        }
        return configuration.template().revisionId();
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

    private List<com.archdox.document.PhotoAsset> toEnginePhotos(InspectionReport report) {
        var photos = new ArrayList<com.archdox.document.PhotoAsset>();
        for (Photo photo : photoRepository.findByOfficeIdAndReportIdOrderByIdDesc(report.officeId(), report.id())) {
            var working = photoAssetRepository.findByPhotoIdAndAssetType(photo.id(), PhotoAssetType.WORKING);
            photos.add(new com.archdox.document.PhotoAsset(
                    String.valueOf(photo.id()),
                    photo.stepCode(),
                    working.map(com.archdox.cloud.photo.domain.PhotoAsset::storageRef).orElse(photo.storageRef()),
                    "",
                    PhotoLayoutSize.MEDIUM));
        }
        return photos;
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        var mapped = new LinkedHashMap<String, Object>();
        rawMap.forEach((key, mapValue) -> mapped.put(String.valueOf(key), mapValue));
        return mapped;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String optionalStringValue(Object value, String fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value);
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return fallback;
    }

    private String jsonString(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize document configuration JSON", ex);
        }
    }

    private DocumentJobResponse toResponse(DocumentJob job) {
        var artifacts = documentArtifactRepository.findByDocumentJobIdOrderById(job.id()).stream()
                .map(this::toArtifactResponse)
                .toList();
        return new DocumentJobResponse(
                job.id(),
                job.officeId(),
                job.reportId(),
                job.projectId(),
                job.reportRevision(),
                job.status(),
                job.progressStep(),
                job.progressPercent(),
                job.progressMessage(),
                job.workerType(),
                job.outputFormat(),
                job.errorCode(),
                job.errorMessage(),
                job.requestedAt(),
                job.startedAt(),
                job.completedAt(),
                artifacts);
    }

    private DocumentArtifactResponse toArtifactResponse(DocumentArtifact artifact) {
        return new DocumentArtifactResponse(
                artifact.id(),
                artifact.artifactType(),
                artifact.storageKind(),
                artifact.storageRef(),
                artifact.fileName(),
                artifact.mimeType(),
                artifact.bytes(),
                artifact.hashSha256(),
                artifact.createdAt());
    }

    private void requireCanRequestGeneration(InspectionReport report) {
        if (!report.canRequestGeneration()) {
            throw new BadRequestException("Inspection report cannot request document generation in status " + report.status());
        }
    }

    private DocumentArtifactType toCloudArtifactType(ArtifactType type) {
        return DocumentArtifactType.valueOf(type.name());
    }

    private String mimeType(ArtifactType type) {
        return switch (type) {
            case DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case PDF -> "application/pdf";
            case PRINT_LOG -> "application/json";
        };
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        var result = new ArrayList<Map<String, Object>>();
        for (var item : rawList) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            var artifact = new LinkedHashMap<String, Object>();
            rawMap.forEach((key, mapValue) -> artifact.put(String.valueOf(key), mapValue));
            result.add(artifact);
        }
        return result;
    }

    private String requiredString(Map<String, Object> payload, String fieldName) {
        var value = payload.get(fieldName);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return String.valueOf(value);
    }

    private String optionalString(Map<String, Object> payload, String fieldName, String fallback) {
        var value = payload.get(fieldName);
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value);
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return 0L;
    }

    private void publishAfterCommit(Object event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventBus.publish(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventBus.publish(event);
            }
        });
    }
}
