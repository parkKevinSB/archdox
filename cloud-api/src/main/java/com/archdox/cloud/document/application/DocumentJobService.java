package com.archdox.cloud.document.application;

import com.archdox.cloud.checklistprint.application.ChecklistDocxExportService;
import com.archdox.cloud.assignment.domain.AssignmentStatus;
import com.archdox.cloud.assignment.domain.ProjectAssignmentRole;
import com.archdox.cloud.assignment.domain.ReportAssignmentRole;
import com.archdox.cloud.assignment.infra.ProjectAssignmentRepository;
import com.archdox.cloud.assignment.infra.ReportAssignmentRepository;
import com.archdox.cloud.document.domain.DocumentArtifact;
import com.archdox.cloud.document.domain.DocumentArtifactStorageKind;
import com.archdox.cloud.document.domain.DocumentArtifactType;
import com.archdox.cloud.document.domain.DocumentJob;
import com.archdox.cloud.document.domain.DocumentJobProgressStep;
import com.archdox.cloud.document.domain.DocumentJobStatus;
import com.archdox.cloud.document.domain.DocumentWorkerType;
import com.archdox.cloud.document.dto.CreateDocumentJobRequest;
import com.archdox.cloud.document.dto.DocumentArtifactResponse;
import com.archdox.cloud.document.dto.DocumentHtmlPreviewResponse;
import com.archdox.cloud.document.dto.DocumentJobResponse;
import com.archdox.cloud.document.event.DocumentGeneratedEvent;
import com.archdox.cloud.document.infra.DocumentArtifactRepository;
import com.archdox.cloud.document.infra.DocumentJobRepository;
import com.archdox.cloud.document.infra.DocumentLocalObjectStore;
import com.archdox.cloud.configuration.application.ConfigurationRegistryService;
import com.archdox.cloud.configuration.application.ResolvedDocumentConfiguration;
import com.archdox.cloud.configuration.domain.ConfigResolutionSource;
import com.archdox.cloud.documenttype.application.DocumentTypeRegistryService;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspection.application.InspectionReportService;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.office.application.OfficePermissionService;
import com.archdox.cloud.office.infra.OfficeRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.photo.domain.Photo;
import com.archdox.cloud.photo.domain.PhotoAssetStatus;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.infra.PhotoAssetRepository;
import com.archdox.cloud.photo.infra.PhotoLocalObjectStore;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.archdox.document.BundledDocumentTemplates;
import com.archdox.document.ArtifactType;
import com.archdox.document.DocumentGenerationRequest;
import com.archdox.document.DocumentExportRequest;
import com.archdox.document.HtmlPreviewDocumentRenderer;
import com.archdox.document.GeneratedArtifact;
import com.archdox.document.LibreOfficeDocumentArtifactExporter;
import com.archdox.document.LibreOfficePdfExportOptions;
import com.archdox.document.OutputFormat;
import com.archdox.document.PhotoLayoutSize;
import com.archdox.document.ResolvedPhotoContent;
import com.archdox.document.TemplateSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.bloom.EventBus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.archdox.shared.MembershipRole;
import com.archdox.shared.OfficeType;

@Service
public class DocumentJobService {
    private static final String REPORT_TYPE_CHECKLIST = "CONSTRUCTION_SUPERVISION_CHECKLIST";
    private static final String DOCX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PDF_MIME_TYPE = "application/pdf";
    private static final int MAX_SIGNATURE_DATA_URL_LENGTH = 1_000_000;
    private static final int MAX_SIGNATURE_IMAGE_BYTES = 750_000;
    private static final Set<String> SUPPORTED_SIGNATURE_MIME_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp");

    private final InspectionReportService inspectionReportService;
    private final InspectionReportRepository reportRepository;
    private final PhotoRepository photoRepository;
    private final PhotoAssetRepository photoAssetRepository;
    private final PhotoLocalObjectStore photoObjectStore;
    private final DocumentJobRepository documentJobRepository;
    private final DocumentArtifactRepository documentArtifactRepository;
    private final DocumentLocalObjectStore objectStore;
    private final OfficePermissionService permissionService;
    private final OfficeRepository officeRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final ReportAssignmentRepository reportAssignmentRepository;
    private final EventBus eventBus;
    private final OperationEventService operationEventService;
    private final ConfigurationRegistryService configurationRegistryService;
    private final DocumentTypeRegistryService documentTypeRegistryService;
    private final DocumentSnapshotBuilder snapshotBuilder;
    private final DocumentGenerationRoutingService routingService;
    private final DocumentPreflightGateService preflightGateService;
    private final ChecklistDocxExportService checklistDocxExportService;
    private final LibreOfficeDocumentArtifactExporter checklistPdfExporter;
    private final ObjectMapper objectMapper;
    private final HtmlPreviewDocumentRenderer htmlPreviewRenderer;

    public DocumentJobService(
            InspectionReportService inspectionReportService,
            InspectionReportRepository reportRepository,
            PhotoRepository photoRepository,
            PhotoAssetRepository photoAssetRepository,
            PhotoLocalObjectStore photoObjectStore,
            DocumentJobRepository documentJobRepository,
            DocumentArtifactRepository documentArtifactRepository,
            DocumentLocalObjectStore objectStore,
            OfficePermissionService permissionService,
            OfficeRepository officeRepository,
            ProjectAssignmentRepository projectAssignmentRepository,
            ReportAssignmentRepository reportAssignmentRepository,
            EventBus eventBus,
            OperationEventService operationEventService,
            ConfigurationRegistryService configurationRegistryService,
            DocumentTypeRegistryService documentTypeRegistryService,
            DocumentSnapshotBuilder snapshotBuilder,
            DocumentGenerationRoutingService routingService,
            DocumentPreflightGateService preflightGateService,
            ChecklistDocxExportService checklistDocxExportService,
            ObjectMapper objectMapper
    ) {
        this.inspectionReportService = inspectionReportService;
        this.reportRepository = reportRepository;
        this.photoRepository = photoRepository;
        this.photoAssetRepository = photoAssetRepository;
        this.photoObjectStore = photoObjectStore;
        this.documentJobRepository = documentJobRepository;
        this.documentArtifactRepository = documentArtifactRepository;
        this.objectStore = objectStore;
        this.permissionService = permissionService;
        this.officeRepository = officeRepository;
        this.projectAssignmentRepository = projectAssignmentRepository;
        this.reportAssignmentRepository = reportAssignmentRepository;
        this.eventBus = eventBus;
        this.operationEventService = operationEventService;
        this.configurationRegistryService = configurationRegistryService;
        this.documentTypeRegistryService = documentTypeRegistryService;
        this.snapshotBuilder = snapshotBuilder;
        this.routingService = routingService;
        this.preflightGateService = preflightGateService;
        this.checklistDocxExportService = checklistDocxExportService;
        this.checklistPdfExporter = new LibreOfficeDocumentArtifactExporter(LibreOfficePdfExportOptions.defaults());
        this.objectMapper = objectMapper;
        this.htmlPreviewRenderer = new HtmlPreviewDocumentRenderer(this::resolvePreviewPhotoContent);
    }

    @Transactional
    public DocumentJobResponse create(Long reportId, CreateDocumentJobRequest request, UserPrincipal principal) {
        var createRequest = request == null ? new CreateDocumentJobRequest(null, null, null, null) : request;
        var report = inspectionReportService.requireReport(reportId);
        permissionService.requireReportWriter(principal.userId(), report.officeId(), report.projectId(), report.id());
        requireCanRequestGeneration(report);
        preflightGateService.requirePassedForGeneration(report);
        requirePhotoAssetsReadyForGeneration(report);
        var outputFormat = createRequest.normalizedOutputFormat();
        var officeId = OfficeContext.requireCurrentOfficeId();
        var workerType = routingService.route(officeId, report.reportType(), outputFormat, createRequest.workerType());
        var now = OffsetDateTime.now();
        var configuration = configurationRegistryService.resolveForDocumentGeneration(officeId, report.reportType());
        var renderOverrides = renderOverrides(createRequest);
        var snapshot = new LinkedHashMap<>(snapshotBuilder.build(report, configuration, renderOverrides));
        var signatureApplied = applySignature(snapshot, createRequest.signature(), principal, now, officeId, report);
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
                        "signatureApplied", signatureApplied,
                        "renderOverrideCount", renderOverrides.size(),
                        "templateRevisionId", selectedTemplateRevisionId(report, configuration) == null
                                ? ""
                                : selectedTemplateRevisionId(report, configuration)));
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public DocumentHtmlPreviewResponse previewHtml(Long reportId, UserPrincipal principal) {
        var report = inspectionReportService.requireReport(reportId);
        permissionService.requireReportWriter(principal.userId(), report.officeId(), report.projectId(), report.id());
        requireCanRequestGeneration(report);
        preflightGateService.requirePassedForGeneration(report);
        requirePhotoAssetsReadyForGeneration(report);
        var officeId = OfficeContext.requireCurrentOfficeId();
        var configuration = configurationRegistryService.resolveForDocumentGeneration(officeId, report.reportType());
        var snapshot = new LinkedHashMap<>(snapshotBuilder.build(report, configuration, List.of()));
        var request = new DocumentGenerationRequest(
                "preview-" + report.id() + "-" + report.generationRevision(),
                String.valueOf(officeId),
                String.valueOf(report.id()),
                templateSpec(snapshot, report),
                snapshot,
                toEnginePhotos(snapshot, report),
                OutputFormat.HTML);
        var artifact = htmlPreviewRenderer.render(request);
        return new DocumentHtmlPreviewResponse(
                report.id(),
                report.reportNo(),
                report.title(),
                artifact.fileName(),
                new String(artifact.content(), StandardCharsets.UTF_8));
    }

    private List<DocumentSnapshotBuilder.RenderOverride> renderOverrides(CreateDocumentJobRequest request) {
        if (request.renderOverrides() == null || request.renderOverrides().isEmpty()) {
            return List.of();
        }
        return request.renderOverrides().stream()
                .filter(override -> override != null)
                .map(override -> new DocumentSnapshotBuilder.RenderOverride(
                        override.path(),
                        override.value(),
                        override.label(),
                        override.source()))
                .toList();
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
        var templatePayload = new LinkedHashMap<String, Object>();
        templatePayload.put("templateCode", request.template().templateCode());
        templatePayload.put("version", request.template().version());
        templatePayload.put("storageRef", request.template().storageRef());
        templatePayload.put("schemaJson", request.template().schemaJson());
        templatePayload.put("composePolicyJson", request.template().composePolicyJson());
        templatePayload.put("contentRequired", request.template().contentRequired());
        if (request.template().storageRef() != null && !request.template().storageRef().isBlank()) {
            templatePayload.put("downloadMethod", "GET");
            templatePayload.put("downloadUrl", "/agent/api/v1/document-jobs/%d/template/content".formatted(job.id()));
        }
        payload.put("template", templatePayload);
        payload.put("photos", request.photos().stream()
                .map(photo -> {
                    var photoPayload = new LinkedHashMap<String, Object>();
                    photoPayload.put("photoId", photo.photoId());
                    photoPayload.put("checklistItemKey", photo.checklistItemKey());
                    photoPayload.put("storageRef", photo.storageRef());
                    photoPayload.put("caption", photo.caption());
                    photoPayload.put("layoutSize", photo.layoutSize().name());
                    photoPayload.put("mimeType", photo.mimeType());
                    if (photo.downloadUrl() != null && !photo.downloadUrl().isBlank()) {
                        photoPayload.put("downloadUrl", photo.downloadUrl());
                    }
                    return photoPayload;
                })
                .toList());
        payload.put("resultStorageKind", DocumentArtifactStorageKind.ARCHDOX_AGENT.name());
        return payload;
    }

    private boolean applySignature(
            Map<String, Object> snapshot,
            CreateDocumentJobRequest.DocumentSignatureRequest request,
            UserPrincipal principal,
            OffsetDateTime signedAt,
            Long officeId,
            InspectionReport report
    ) {
        if (request == null) {
            return false;
        }
        var signedByName = normalizeText(request.signedByName());
        var imageDataUrl = normalizeText(request.signatureImageDataUrl());
        if (signedByName.isBlank()) {
            throw new BadRequestException(
                    "DOCUMENT_SIGNATURE_NAME_REQUIRED",
                    "errors.document.signatureNameRequired",
                    "Document signature signer name is required.");
        }
        if (imageDataUrl.isBlank()) {
            throw new BadRequestException(
                    "DOCUMENT_SIGNATURE_IMAGE_REQUIRED",
                    "errors.document.signatureImageRequired",
                    "Document signature image is required.");
        }
        var imageMimeType = validateSignatureImageDataUrl(imageDataUrl, request.signatureImageMimeType());
        var signature = new LinkedHashMap<String, Object>();
        signature.put("signed", true);
        signature.put("signedByUserId", principal.userId());
        signature.put("signedByName", signedByName);
        signature.put("signedByRole", normalizeText(request.signedByRole()));
        signature.put("signedAt", signedAt.toString());
        signature.put("imageMimeType", imageMimeType);
        signature.put("imageDataUrl", imageDataUrl);
        signature.put("signatureSlots", resolveSignatureSlots(officeId, report, principal.userId(), request.signedByRole()));
        snapshot.put("signature", signature);

        var templateFields = mutableMap(snapshot.get("templateFields"));
        templateFields.put("signedByName", signedByName);
        templateFields.put("signedByRole", normalizeText(request.signedByRole()));
        templateFields.put("signedAt", signedAt.toString());
        templateFields.put("signatureSignedByName", signedByName);
        templateFields.put("signatureSignedByRole", normalizeText(request.signedByRole()));
        templateFields.put("signatureSignedAt", signedAt.toString());
        snapshot.put("templateFields", templateFields);
        return true;
    }

    private List<String> resolveSignatureSlots(
            Long officeId,
            InspectionReport report,
            Long userId,
            String requestedRole
    ) {
        var office = officeRepository.findById(officeId)
                .orElseThrow(() -> new NotFoundException("Office not found"));
        if (office.type() == OfficeType.PERSONAL) {
            return List.of("CHIEF_SUPERVISOR", "ARCHITECT_ASSISTANT", "WRITER", "REVIEWER");
        }

        var slots = new LinkedHashSet<String>();
        reportAssignmentRepository.findByOfficeIdAndReportIdAndUserId(officeId, report.id(), userId)
                .filter(assignment -> assignment.status() == AssignmentStatus.ACTIVE)
                .ifPresent(assignment -> addReportAssignmentSlots(slots, assignment.role()));
        projectAssignmentRepository.findByOfficeIdAndProjectIdAndUserId(officeId, report.projectId(), userId)
                .filter(assignment -> assignment.status() == AssignmentStatus.ACTIVE)
                .ifPresent(assignment -> addProjectAssignmentSlots(slots, assignment.role()));

        if (slots.isEmpty()) {
            var membership = permissionService.requireActiveMembership(userId, officeId);
            if (membership.role() == MembershipRole.OWNER || membership.role() == MembershipRole.ADMIN) {
                slots.add("CHIEF_SUPERVISOR");
            }
        }
        if (slots.isEmpty()) {
            addRequestedRoleSlots(slots, requestedRole);
        }
        if (slots.isEmpty()) {
            slots.add("WRITER");
        }
        return List.copyOf(slots);
    }

    private void addReportAssignmentSlots(LinkedHashSet<String> slots, ReportAssignmentRole role) {
        if (role == ReportAssignmentRole.WRITER) {
            slots.add("ARCHITECT_ASSISTANT");
            slots.add("WRITER");
        } else if (role == ReportAssignmentRole.REVIEWER) {
            slots.add("REVIEWER");
        }
    }

    private void addProjectAssignmentSlots(LinkedHashSet<String> slots, ProjectAssignmentRole role) {
        if (role == ProjectAssignmentRole.MANAGER) {
            slots.add("CHIEF_SUPERVISOR");
        } else if (role == ProjectAssignmentRole.REPORT_WRITER) {
            slots.add("ARCHITECT_ASSISTANT");
            slots.add("WRITER");
        }
    }

    private void addRequestedRoleSlots(LinkedHashSet<String> slots, String requestedRole) {
        var role = normalizeText(requestedRole);
        var normalized = role.toUpperCase();
        if (normalized.contains("CHIEF")
                || normalized.contains("MANAGER")
                || role.contains("총괄")
                || role.contains("책임")) {
            slots.add("CHIEF_SUPERVISOR");
        }
        if (normalized.contains("ASSISTANT")
                || normalized.contains("WRITER")
                || role.contains("건축사보")
                || role.contains("감리사보")) {
            slots.add("ARCHITECT_ASSISTANT");
            slots.add("WRITER");
        }
        if (normalized.contains("REVIEWER") || role.contains("검토")) {
            slots.add("REVIEWER");
        }
    }

    private String validateSignatureImageDataUrl(String dataUrl, String requestedMimeType) {
        if (dataUrl.length() > MAX_SIGNATURE_DATA_URL_LENGTH) {
            throw new BadRequestException(
                    "DOCUMENT_SIGNATURE_IMAGE_TOO_LARGE",
                    "errors.document.signatureImageTooLarge",
                    "Document signature image is too large.");
        }
        var comma = dataUrl.indexOf(',');
        if (!dataUrl.startsWith("data:") || comma < 0) {
            throw new BadRequestException(
                    "DOCUMENT_SIGNATURE_IMAGE_INVALID",
                    "errors.document.signatureImageInvalid",
                    "Document signature image must be a base64 data URL.");
        }
        var metadata = dataUrl.substring("data:".length(), comma).toLowerCase();
        if (!metadata.endsWith(";base64")) {
            throw new BadRequestException(
                    "DOCUMENT_SIGNATURE_IMAGE_INVALID",
                    "errors.document.signatureImageInvalid",
                    "Document signature image must be base64 encoded.");
        }
        var mimeType = metadata.substring(0, metadata.length() - ";base64".length());
        var normalizedRequestedMimeType = normalizeText(requestedMimeType).toLowerCase();
        if (!normalizedRequestedMimeType.isBlank() && !normalizedRequestedMimeType.equals(mimeType)) {
            throw new BadRequestException(
                    "DOCUMENT_SIGNATURE_IMAGE_MIME_MISMATCH",
                    "errors.document.signatureImageMimeMismatch",
                    "Document signature image MIME type does not match the data URL.");
        }
        if (!SUPPORTED_SIGNATURE_MIME_TYPES.contains(mimeType)) {
            throw new BadRequestException(
                    "DOCUMENT_SIGNATURE_IMAGE_UNSUPPORTED",
                    "errors.document.signatureImageUnsupported",
                    "Document signature image type is not supported.");
        }
        var base64 = dataUrl.substring(comma + 1);
        try {
            var decoded = Base64.getDecoder().decode(base64);
            if (decoded.length == 0 || decoded.length > MAX_SIGNATURE_IMAGE_BYTES) {
                throw new BadRequestException(
                        "DOCUMENT_SIGNATURE_IMAGE_TOO_LARGE",
                        "errors.document.signatureImageTooLarge",
                        "Document signature image is too large.");
            }
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(
                    "DOCUMENT_SIGNATURE_IMAGE_INVALID",
                    "errors.document.signatureImageInvalid",
                    "Document signature image is not valid base64.");
        }
        return mimeType;
    }

    private Map<String, Object> mutableMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return new LinkedHashMap<>();
        }
        var result = new LinkedHashMap<String, Object>();
        raw.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildArchDoxAgentRenderCommandPayload(Long officeId, Long jobId) {
        var job = requireFlowJob(officeId, jobId);
        if (job.workerType() != DocumentWorkerType.ARCHDOX_AGENT) {
            throw new DocumentGenerationException("UNSUPPORTED_WORKER_TYPE", "Document job is not routed to ARCHDOX_AGENT");
        }
        var payload = new LinkedHashMap<String, Object>();
        payload.put("documentJobId", job.id());
        payload.put("officeId", job.officeId());
        payload.put("reportId", job.reportId());
        payload.put("outputFormat", job.outputFormat().name());
        payload.put("renderPackageMethod", "GET");
        payload.put("renderPackageUrl", "/agent/api/v1/document-jobs/%d/render-package".formatted(job.id()));
        payload.put("resultStorageKind", DocumentArtifactStorageKind.ARCHDOX_AGENT.name());
        return payload;
    }

    @Transactional(readOnly = true)
    public DocumentArtifactDownload downloadArchDoxAgentTemplate(Long officeId, Long jobId) throws IOException {
        var job = requireFlowJob(officeId, jobId);
        var report = requireFlowReport(officeId, job.reportId());
        if (job.workerType() != DocumentWorkerType.ARCHDOX_AGENT) {
            throw new BadRequestException("Document job is not routed to ARCHDOX_AGENT");
        }
        var template = templateSpec(job, report);
        if (template.storageRef() == null || template.storageRef().isBlank()) {
            throw new NotFoundException("Document template content not found");
        }
        if (shouldPreferBundledTemplate(template.storageRef())) {
            var bundled = BundledDocumentTemplates.read(template.storageRef());
            if (bundled.isPresent()) {
                var content = bundled.get();
                return new DocumentArtifactDownload(
                        filenameFromStorageRef(template.storageRef()),
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        content.length,
                        outputStream -> outputStream.write(content));
            }
        }
        if (!objectStore.exists(template.storageRef())) {
            var bundled = BundledDocumentTemplates.read(template.storageRef());
            if (bundled.isPresent()) {
                var content = bundled.get();
                return new DocumentArtifactDownload(
                        filenameFromStorageRef(template.storageRef()),
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        content.length,
                        outputStream -> outputStream.write(content));
            }
            throw new NotFoundException("Document template content not found");
        }
        return new DocumentArtifactDownload(
                filenameFromStorageRef(template.storageRef()),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                objectStore.size(template.storageRef()),
                outputStream -> objectStore.copyTo(template.storageRef(), outputStream));
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

    @Transactional(readOnly = true)
    public boolean isCloudApiChecklistDocument(Long officeId, Long jobId) {
        var job = requireFlowJob(officeId, jobId);
        var report = requireFlowReport(officeId, job.reportId());
        return job.workerType() == DocumentWorkerType.CLOUD_API
                && REPORT_TYPE_CHECKLIST.equals(report.reportType());
    }

    @Transactional
    public void completeCloudApiChecklistDocument(Long officeId, Long jobId) {
        var job = requireFlowJob(officeId, jobId);
        var report = requireFlowReport(officeId, job.reportId());
        if (job.status() == DocumentJobStatus.GENERATED) {
            return;
        }
        if (job.workerType() != DocumentWorkerType.CLOUD_API) {
            throw new DocumentGenerationException(
                    "UNSUPPORTED_WORKER_TYPE",
                    "Document job is not routed to CLOUD_API");
        }
        if (!REPORT_TYPE_CHECKLIST.equals(report.reportType())) {
            throw new DocumentGenerationException(
                    "UNSUPPORTED_REPORT_TYPE",
                    "CLOUD_API document generation only supports construction supervision checklist reports");
        }
        if (job.outputFormat() != OutputFormat.DOCX && job.outputFormat() != OutputFormat.PDF) {
            throw new DocumentGenerationException(
                    "UNSUPPORTED_OUTPUT_FORMAT",
                    "Construction supervision checklist generation supports DOCX and PDF");
        }

        var now = OffsetDateTime.now();
        job.markGenerating(now);
        report.markGenerating(now);
        job.updateProgress(
                DocumentJobProgressStep.RENDERING,
                70,
                "선택한 감리일지를 기준으로 체크리스트 문서를 생성하는 중입니다.",
                now);
        var export = checklistDocxExportService.exportSystem(officeId, report.id(), null);
        var artifactPayload = job.outputFormat() == OutputFormat.PDF
                ? convertChecklistPdf(job, report, export)
                : checklistDocxArtifact(job, export);
        try {
            objectStore.write(artifactPayload.storageRef(), artifactPayload.content());
        } catch (IOException ex) {
            throw new DocumentGenerationException(
                    "DOCUMENT_ARTIFACT_WRITE_FAILED",
                    "Failed to save checklist document artifact",
                    ex);
        }

        job.updateProgress(
                DocumentJobProgressStep.STORING_ARTIFACTS,
                90,
                "체크리스트 파일 정보를 저장하는 중입니다.",
                OffsetDateTime.now());
        documentArtifactRepository.deleteByDocumentJobId(job.id());
        var artifact = documentArtifactRepository.save(new DocumentArtifact(
                job.officeId(),
                job.id(),
                job.reportId(),
                artifactPayload.artifactType(),
                DocumentArtifactStorageKind.API_LOCAL,
                artifactPayload.storageRef(),
                artifactPayload.fileName(),
                artifactPayload.mimeType(),
                artifactPayload.content().length,
                sha256(artifactPayload.content()),
                OffsetDateTime.now()));
        var completedAt = OffsetDateTime.now();
        job.markGenerated(completedAt);
        report.markGenerated(job.reportRevision(), completedAt);
        recordGenerated(job, "체크리스트 " + job.outputFormat().name() + " 생성이 완료되었습니다.", 1);
        publishAfterCommit(new DocumentGeneratedEvent(
                job.officeId(),
                job.reportId(),
                job.id(),
                List.of(artifact.id()),
                completedAt));
    }

    private ChecklistArtifactPayload checklistDocxArtifact(DocumentJob job, com.archdox.cloud.checklistprint.dto.ChecklistDocxExport export) {
        return new ChecklistArtifactPayload(
                DocumentArtifactType.DOCX,
                checklistStorageRef(job, export.fileName()),
                export.fileName(),
                DOCX_MIME_TYPE,
                export.content());
    }

    private ChecklistArtifactPayload convertChecklistPdf(
            DocumentJob job,
            InspectionReport report,
            com.archdox.cloud.checklistprint.dto.ChecklistDocxExport export
    ) {
        var docxStorageRef = checklistStorageRef(job, export.fileName());
        var source = new GeneratedArtifact(
                ArtifactType.DOCX,
                export.fileName(),
                docxStorageRef,
                export.content().length,
                sha256(export.content()),
                export.content());
        var result = checklistPdfExporter.export(new DocumentExportRequest(
                String.valueOf(job.id()),
                String.valueOf(report.id()),
                templateSpec(Map.of(), report),
                source,
                ArtifactType.PDF,
                Map.of()));
        if (!result.isCompleted()) {
            throw new DocumentGenerationException(
                    result.errorCode() == null ? "DOCUMENT_PDF_EXPORT_FAILED" : result.errorCode(),
                    result.errorMessage() == null ? "Failed to export checklist PDF" : result.errorMessage());
        }
        var artifact = result.artifact();
        return new ChecklistArtifactPayload(
                DocumentArtifactType.PDF,
                artifact.storageRef(),
                artifact.fileName(),
                PDF_MIME_TYPE,
                artifact.content());
    }

    private record ChecklistArtifactPayload(
            DocumentArtifactType artifactType,
            String storageRef,
            String fileName,
            String mimeType,
            byte[] content
    ) {
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
                toEnginePhotos(job, report),
                job.outputFormat());
    }

    private String filenameFromStorageRef(String storageRef) {
        var normalized = storageRef.replace('\\', '/');
        var index = normalized.lastIndexOf('/');
        if (index >= 0 && index + 1 < normalized.length()) {
            return normalized.substring(index + 1);
        }
        return "template.docx";
    }

    private String checklistStorageRef(DocumentJob job, String fileName) {
        return "documents/offices/%d/reports/%d/jobs/%d/%s".formatted(
                job.officeId(),
                job.reportId(),
                job.id(),
                safeFileName(fileName));
    }

    private String safeFileName(String fileName) {
        var normalized = fileName == null || fileName.isBlank() ? "checklist.docx" : fileName.trim();
        return normalized.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String sha256(byte[] content) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private boolean shouldPreferBundledTemplate(String storageRef) {
        if (storageRef == null || storageRef.isBlank()) {
            return false;
        }
        var normalized = storageRef.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.startsWith("templates/korean/");
    }

    @SuppressWarnings("unchecked")
    private TemplateSpec templateSpec(DocumentJob job, InspectionReport report) {
        return templateSpec(job.inputSnapshotJson(), report);
    }

    @SuppressWarnings("unchecked")
    private TemplateSpec templateSpec(Map<String, Object> snapshot, InspectionReport report) {
        var configuration = mapValue(snapshot.get("configuration"));
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
                    jsonString(template.get("composePolicy")),
                    null,
                    true);
        }
        var defaultTemplate = documentTypeRegistryService.resolveByReportType(report.officeId(), report.reportType())
                .filter(definition -> definition.defaultTemplateStorageRef() != null
                        && !definition.defaultTemplateStorageRef().isBlank())
                .map(definition -> new TemplateSpec(
                        optionalStringValue(definition.defaultTemplateCode(), report.reportType()),
                        1,
                        definition.defaultTemplateStorageRef(),
                        "{}",
                        "{}",
                        null,
                        true));
        if (defaultTemplate.isPresent()) {
            return defaultTemplate.get();
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

    private List<com.archdox.document.PhotoAsset> toEnginePhotos(DocumentJob job, InspectionReport report) {
        return toEnginePhotos(job.inputSnapshotJson(), report);
    }

    private List<com.archdox.document.PhotoAsset> toEnginePhotos(Map<String, Object> snapshot, InspectionReport report) {
        var snapshotPhotos = listValue(snapshot.get("photos"));
        if (!snapshotPhotos.isEmpty()) {
            var photos = new ArrayList<com.archdox.document.PhotoAsset>();
            for (Object rawPhoto : snapshotPhotos) {
                var photo = mapValue(rawPhoto);
                var photoId = optionalStringValue(photo.get("photoId"), "");
                var storageRef = firstNonBlank(photo, "workingStorageRef", "storageRef", "thumbnailStorageRef");
                if (photoId.isBlank() && storageRef.isBlank()) {
                    continue;
                }
                photos.add(new com.archdox.document.PhotoAsset(
                        photoId,
                        firstNonBlank(photo, "checklistItemCode", "stepCode", "checklistItemId"),
                        storageRef,
                        firstNonBlank(photo, "caption", "checklistItemLabel"),
                        PhotoLayoutSize.MEDIUM,
                        optionalStringValue(photo.get("mimeType"), "image/jpeg"),
                        photoId.isBlank() ? null : "/agent/api/v1/photos/%s/assets/WORKING/content".formatted(photoId)));
            }
            return photos;
        }
        var photos = new ArrayList<com.archdox.document.PhotoAsset>();
        for (Photo photo : photoRepository.findByOfficeIdAndReportIdAndStatusNotOrderByIdDesc(
                report.officeId(),
                report.id(),
                PhotoStatus.DELETED)) {
            var working = photoAssetRepository.findByPhotoIdAndAssetType(photo.id(), PhotoAssetType.WORKING);
            if (working.isEmpty() || working.get().status() != PhotoAssetStatus.UPLOADED) {
                continue;
            }
            photos.add(new com.archdox.document.PhotoAsset(
                    String.valueOf(photo.id()),
                    photo.stepCode(),
                    working.get().storageRef(),
                    "",
                    PhotoLayoutSize.MEDIUM,
                    working.get().mimeType(),
                    "/agent/api/v1/photos/%d/assets/WORKING/content".formatted(photo.id())));
        }
        return photos;
    }

    private Optional<ResolvedPhotoContent> resolvePreviewPhotoContent(com.archdox.document.PhotoAsset photo) throws IOException {
        if (photo == null || photo.storageRef() == null || photo.storageRef().isBlank()) {
            return Optional.empty();
        }
        if (!photoObjectStore.exists(photo.storageRef())) {
            return Optional.empty();
        }
        try (var input = photoObjectStore.open(photo.storageRef())) {
            return Optional.of(new ResolvedPhotoContent(
                    input.readAllBytes(),
                    optionalStringValue(photo.mimeType(), "image/jpeg")));
        }
    }

    private void requirePhotoAssetsReadyForGeneration(InspectionReport report) {
        var pendingPhotoIds = new ArrayList<Long>();
        for (Photo photo : photoRepository.findByOfficeIdAndReportIdAndStatusNotOrderByIdDesc(
                report.officeId(),
                report.id(),
                PhotoStatus.DELETED)) {
            var working = photoAssetRepository.findByPhotoIdAndAssetType(photo.id(), PhotoAssetType.WORKING);
            if (working.isEmpty() || working.get().status() != PhotoAssetStatus.UPLOADED) {
                pendingPhotoIds.add(photo.id());
            }
        }
        if (!pendingPhotoIds.isEmpty()) {
            throw new BadRequestException(
                    "PHOTO_WORKING_ASSET_NOT_READY",
                    "errors.document.photoWorkingAssetNotReady",
                    "Photo working images are still being prepared. Please try document generation again shortly.",
                    Map.of(
                            "reportId", report.id(),
                            "pendingPhotoIds", pendingPhotoIds));
        }
    }

    private List<?> listValue(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
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

    private String firstNonBlank(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            var value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return "";
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
