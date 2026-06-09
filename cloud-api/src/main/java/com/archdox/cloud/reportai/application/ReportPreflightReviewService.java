package com.archdox.cloud.reportai.application;

import com.archdox.cloud.aipolicy.application.AiFeature;
import com.archdox.cloud.aipolicy.application.AiModelCallMetadata;
import com.archdox.cloud.aipolicy.application.AiPolicyExecutionService;
import com.archdox.cloud.documentai.application.DocumentAiReviewProperties;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.office.application.OfficePermissionService;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.photo.domain.Photo;
import com.archdox.cloud.photo.domain.PhotoAsset;
import com.archdox.cloud.photo.domain.PhotoAssetStatus;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.infra.PhotoAssetRepository;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewFinding;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewRun;
import com.archdox.cloud.reportai.domain.ReportPreflightFindingResolutionStatus;
import com.archdox.cloud.reportai.dto.ReportPreflightReviewFindingResponse;
import com.archdox.cloud.reportai.dto.ReportPreflightReviewRunResponse;
import com.archdox.cloud.reportai.dto.ResolveReportPreflightFindingRequest;
import com.archdox.cloud.reportai.flow.ReportPreflightReviewFlowFactory;
import com.archdox.cloud.reportai.flow.ReportPreflightReviewRequest;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewFindingRepository;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewRunRepository;
import com.archdox.documentai.ReportPreflightHarnessFactory;
import com.archdox.documentai.ReportPreflightInput;
import com.archdox.documentai.ReportPreflightResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlowFactory;
import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.refine.MaxAttemptsRefinePolicy;
import io.github.parkkevinsb.flower.ai.harness.spec.AiHarnessSpec;
import io.github.parkkevinsb.flower.ai.harness.spi.TraceListener;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportPreflightReviewService {
    private final InspectionReportRepository reportRepository;
    private final InspectionReportStepRepository stepRepository;
    private final PhotoRepository photoRepository;
    private final PhotoAssetRepository photoAssetRepository;
    private final OfficePermissionService permissionService;
    private final ReportPreflightReviewRunRepository runRepository;
    private final ReportPreflightReviewFindingRepository findingRepository;
    private final ReportPreflightReviewFlowFactory flowFactory;
    private final DocumentAiReviewProperties aiReviewProperties;
    private final AiPolicyExecutionService aiPolicyExecutionService;
    private final AiModelGateway aiModelGateway;
    private final ReportPreflightAiReviewRunStore aiReviewRunStore;
    private final ReportPreflightAiReviewFindingSink aiReviewFindingSink;
    private final ObjectMapper objectMapper;
    private final OperationEventService operationEventService;
    private final TraceListener aiHarnessTraceListener;

    public ReportPreflightReviewService(
            InspectionReportRepository reportRepository,
            InspectionReportStepRepository stepRepository,
            PhotoRepository photoRepository,
            PhotoAssetRepository photoAssetRepository,
            OfficePermissionService permissionService,
            ReportPreflightReviewRunRepository runRepository,
            ReportPreflightReviewFindingRepository findingRepository,
            ReportPreflightReviewFlowFactory flowFactory,
            DocumentAiReviewProperties aiReviewProperties,
            AiPolicyExecutionService aiPolicyExecutionService,
            AiModelGateway aiModelGateway,
            ReportPreflightAiReviewRunStore aiReviewRunStore,
            ReportPreflightAiReviewFindingSink aiReviewFindingSink,
            ObjectMapper objectMapper,
            OperationEventService operationEventService,
            TraceListener aiHarnessTraceListener
    ) {
        this.reportRepository = reportRepository;
        this.stepRepository = stepRepository;
        this.photoRepository = photoRepository;
        this.photoAssetRepository = photoAssetRepository;
        this.permissionService = permissionService;
        this.runRepository = runRepository;
        this.findingRepository = findingRepository;
        this.flowFactory = flowFactory;
        this.aiReviewProperties = aiReviewProperties;
        this.aiPolicyExecutionService = aiPolicyExecutionService;
        this.aiModelGateway = aiModelGateway;
        this.aiReviewRunStore = aiReviewRunStore;
        this.aiReviewFindingSink = aiReviewFindingSink;
        this.objectMapper = objectMapper;
        this.operationEventService = operationEventService;
        this.aiHarnessTraceListener = aiHarnessTraceListener;
    }

    @Transactional
    public ReportPreflightReviewSubmission requestReview(Long reportId, UserPrincipal principal) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        var report = reportRepository.findByIdAndOfficeId(reportId, officeId)
                .orElseThrow(() -> new NotFoundException("Inspection report not found"));
        permissionService.requireReportWriter(principal.userId(), report.officeId(), report.projectId(), report.id());
        var run = runRepository.saveAndFlush(new ReportPreflightReviewRun(
                officeId,
                report.id(),
                report.contentRevision(),
                principal.userId(),
                OffsetDateTime.now()));
        var aiHarnessFlow = createAiHarnessFlow(report, run);
        operationEventService.record(
                officeId,
                OperationEventSeverity.INFO,
                "REPORT_PREFLIGHT_REVIEW_REQUESTED",
                "report-preflight-review",
                "report:" + report.id() + ":preflight-run:" + run.id(),
                "REPORT_PREFLIGHT_REVIEW_RUN",
                run.id(),
                principal.userId(),
                null,
                "Report preflight review requested.",
                requestPayload(report, run, aiHarnessFlow != null));
        var request = new ReportPreflightReviewRequest(officeId, report.id(), run.id(), principal.userId());
        return new ReportPreflightReviewSubmission(toResponse(run), flowFactory.create(request, aiHarnessFlow));
    }

    @Transactional(readOnly = true)
    public List<ReportPreflightReviewRunResponse> listRuns(Long reportId, UserPrincipal principal) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        var report = requireReportWithWriteAccess(reportId, officeId, principal);
        return runRepository.findByOfficeIdAndReportIdOrderByRequestedAtDesc(officeId, report.id()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReportPreflightReviewFindingResponse> listFindings(Long reportId, Long runId, UserPrincipal principal) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        var report = requireReportWithWriteAccess(reportId, officeId, principal);
        runRepository.findByIdAndOfficeIdAndReportId(runId, officeId, report.id())
                .orElseThrow(() -> new NotFoundException("Report preflight review run not found"));
        return findingRepository.findByOfficeIdAndReviewRunIdOrderByIdAsc(officeId, runId).stream()
                .map(this::toFindingResponse)
                .toList();
    }

    @Transactional
    public ReportPreflightReviewFindingResponse resolveFinding(
            Long reportId,
            Long runId,
            Long findingId,
            ResolveReportPreflightFindingRequest request,
            UserPrincipal principal
    ) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        var report = requireReportWithWriteAccess(reportId, officeId, principal);
        var run = runRepository.findByIdAndOfficeIdAndReportId(runId, officeId, report.id())
                .orElseThrow(() -> new NotFoundException("Report preflight review run not found"));
        var finding = findingRepository.findByIdAndOfficeIdAndReviewRunIdAndReportId(findingId, officeId, run.id(), report.id())
                .orElseThrow(() -> new NotFoundException("Report preflight review finding not found"));
        finding.resolve(resolutionStatus(request), request == null ? null : request.resolutionNote(), principal.userId(), OffsetDateTime.now());
        recomputeRunAfterResolution(run);
        operationEventService.record(
                officeId,
                OperationEventSeverity.INFO,
                "REPORT_PREFLIGHT_FINDING_RESOLVED",
                "report-preflight-review",
                "report:" + report.id() + ":preflight-run:" + run.id(),
                "REPORT_PREFLIGHT_REVIEW_FINDING",
                finding.id(),
                principal.userId(),
                null,
                "Report preflight finding resolution updated.",
                Map.of(
                        "reportId", report.id(),
                        "reviewRunId", run.id(),
                        "findingId", finding.id(),
                        "resolutionStatus", finding.resolutionStatus().name()));
        return toFindingResponse(finding);
    }

    private com.archdox.cloud.inspection.domain.InspectionReport requireReportWithWriteAccess(
            Long reportId,
            Long officeId,
            UserPrincipal principal
    ) {
        var report = reportRepository.findByIdAndOfficeId(reportId, officeId)
                .orElseThrow(() -> new NotFoundException("Inspection report not found"));
        permissionService.requireReportWriter(principal.userId(), report.officeId(), report.projectId(), report.id());
        return report;
    }

    private ReportPreflightReviewRunResponse toResponse(ReportPreflightReviewRun run) {
        return new ReportPreflightReviewRunResponse(
                run.id(),
                run.officeId(),
                run.reportId(),
                run.reportRevision(),
                run.status().name(),
                run.requestedBy(),
                run.terminalReason(),
                run.hasHarness(),
                run.harnessRunId(),
                run.harnessStatus(),
                run.harnessAttempt(),
                run.harnessTerminalReason(),
                run.aiProviderCode(),
                run.aiModelId(),
                run.requestedAt(),
                run.updatedAt(),
                run.completedAt());
    }

    private ReportPreflightReviewFindingResponse toFindingResponse(ReportPreflightReviewFinding finding) {
        return new ReportPreflightReviewFindingResponse(
                finding.id(),
                finding.source(),
                finding.code(),
                finding.severity(),
                finding.location(),
                finding.message(),
                finding.evidence(),
                finding.attributesJson(),
                finding.resolutionStatus().name(),
                finding.resolutionNote(),
                finding.resolvedBy(),
                finding.resolvedAt(),
                finding.createdAt());
    }

    private void recomputeRunAfterResolution(ReportPreflightReviewRun run) {
        if (!"NEEDS_ATTENTION".equals(run.status().name())) {
            return;
        }
        var findings = findingRepository.findByOfficeIdAndReviewRunIdOrderByIdAsc(run.officeId(), run.id());
        var openBlocking = findings.stream()
                .anyMatch(finding -> isBlockingSeverity(finding.severity())
                        && finding.resolutionStatus() == ReportPreflightFindingResolutionStatus.OPEN);
        if (!openBlocking) {
            run.markPassed("PREFLIGHT_FINDINGS_RESOLVED", OffsetDateTime.now());
        }
    }

    private ReportPreflightFindingResolutionStatus resolutionStatus(ResolveReportPreflightFindingRequest request) {
        var value = request == null ? null : request.resolutionStatus();
        if (value == null || value.isBlank()) {
            return ReportPreflightFindingResolutionStatus.RESOLVED;
        }
        try {
            var status = ReportPreflightFindingResolutionStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (status == ReportPreflightFindingResolutionStatus.OPEN) {
                throw new BadRequestException("OPEN resolution status cannot be submitted");
            }
            return status;
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid preflight finding resolution status");
        }
    }

    private boolean isBlockingSeverity(String severity) {
        return "HIGH".equals(severity) || "CRITICAL".equals(severity);
    }

    private AiHarnessFlow createAiHarnessFlow(InspectionReport report, ReportPreflightReviewRun run) {
        var aiPlan = resolveAiPlan(report.officeId(), run.requestedBy());
        if (aiPlan.isEmpty()) {
            return null;
        }
        AiHarnessSpec<ReportPreflightInput, ReportPreflightResult> spec =
                new ReportPreflightHarnessFactory(objectMapper).spec(
                        aiReviewFindingSink,
                        aiReviewRunStore,
                        new MaxAttemptsRefinePolicy(2),
                        aiHarnessTraceListener);
        var overrides = AiHarnessFlowFactory.RunOverrides.builder()
                .modelId(aiPlan.get().modelId())
                .providerOptions(AiModelCallMetadata.options(
                        report.officeId(),
                        aiPlan.get().userId(),
                        AiFeature.DOCUMENT_REVIEW.name(),
                        "report-preflight-review",
                        "report:" + report.id() + ":preflight-run:" + run.id(),
                        "REPORT_PREFLIGHT_REVIEW_RUN",
                        run.id(),
                        Map.of(
                                "archdox.reportId", report.id(),
                                "archdox.reviewRunId", run.id()),
                        aiPlan.get().maxOutputTokens()))
                .build();
        var flow = new AiHarnessFlowFactory<>(aiModelGateway, spec, Instant::now)
                .createFlow(input(report), overrides);
        run.attachHarness(
                flow.context().runId().value(),
                flow.context().harnessId(),
                flow.context().promptVersion(),
                aiPlan.get().provider().providerCode(),
                aiPlan.get().modelId().asString(),
                OffsetDateTime.now());
        return flow;
    }

    private Optional<com.archdox.cloud.aipolicy.application.AiExecutionPlan> resolveAiPlan(Long officeId, Long userId) {
        if (!aiReviewProperties.isEnabled()) {
            return Optional.empty();
        }
        return aiPolicyExecutionService.findAllowed(officeId, userId, AiFeature.DOCUMENT_REVIEW);
    }

    private ReportPreflightInput input(InspectionReport report) {
        return new ReportPreflightInput(
                String.valueOf(report.officeId()),
                String.valueOf(report.id()),
                report.reportType(),
                report.title(),
                report.status().name(),
                report.contentRevision(),
                reportSnapshot(report),
                stepSnapshot(report),
                photoSnapshot(report),
                List.of());
    }

    private Map<String, Object> reportSnapshot(InspectionReport report) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("id", report.id());
        snapshot.put("officeId", report.officeId());
        snapshot.put("projectId", report.projectId());
        snapshot.put("siteId", report.siteId() == null ? "" : report.siteId());
        snapshot.put("reportNo", report.reportNo());
        snapshot.put("reportType", report.reportType());
        snapshot.put("title", report.title() == null ? "" : report.title());
        snapshot.put("status", report.status().name());
        snapshot.put("currentStep", report.currentStep() == null ? "" : report.currentStep());
        snapshot.put("templateId", report.templateId() == null ? "" : report.templateId());
        snapshot.put("contentRevision", report.contentRevision());
        snapshot.put("submittedRevision", report.submittedRevision() == null ? "" : report.submittedRevision());
        return snapshot;
    }

    private Map<String, Object> stepSnapshot(InspectionReport report) {
        var snapshot = new LinkedHashMap<String, Object>();
        for (var step : stepRepository.findByReportIdOrderById(report.id())) {
            var stepValue = new LinkedHashMap<String, Object>();
            stepValue.put("payloadStorageMode", step.payloadStorageMode().name());
            stepValue.put("payload", step.payloadJson() == null ? Map.of() : step.payloadJson());
            stepValue.put("clientRevision", step.clientRevision());
            stepValue.put("savedAt", step.savedAt().toString());
            snapshot.put(step.stepCode(), stepValue);
        }
        return snapshot;
    }

    private List<Map<String, Object>> photoSnapshot(InspectionReport report) {
        var photos = photoRepository.findByOfficeIdAndReportIdAndStatusNotOrderByIdDesc(
                report.officeId(),
                report.id(),
                PhotoStatus.DELETED);
        if (photos.isEmpty()) {
            return List.of();
        }
        var assetsByPhotoId = photoAssetRepository.findByPhotoIdInOrderByPhotoIdAscIdAsc(
                        photos.stream().map(Photo::id).toList()).stream()
                .collect(Collectors.groupingBy(asset -> asset.photo().id()));
        return photos.stream()
                .map(photo -> photoSnapshot(photo, assetsByPhotoId.getOrDefault(photo.id(), List.of())))
                .toList();
    }

    private Map<String, Object> photoSnapshot(Photo photo, List<PhotoAsset> assets) {
        var assetsByType = assets.stream()
                .collect(Collectors.toMap(PhotoAsset::assetType, Function.identity(), (left, right) -> left));
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("photoId", photo.id());
        snapshot.put("stepCode", photo.stepCode() == null ? "" : photo.stepCode());
        snapshot.put("checklistItemId", photo.checklistItemId() == null ? "" : photo.checklistItemId());
        snapshot.put("captureKind", photo.captureKind().name());
        snapshot.put("status", photo.status().name());
        snapshot.put("mime", photo.mimeType());
        snapshot.put("bytes", photo.bytes());
        snapshot.put("width", photo.width() == null ? "" : photo.width());
        snapshot.put("height", photo.height() == null ? "" : photo.height());
        snapshot.put("workingUploaded", uploaded(assetsByType.get(PhotoAssetType.WORKING)));
        snapshot.put("thumbnailUploaded", uploaded(assetsByType.get(PhotoAssetType.THUMBNAIL)));
        snapshot.put("originalUploaded", uploaded(assetsByType.get(PhotoAssetType.ORIGINAL)));
        snapshot.put("originalPickupStatus", photo.originalPickupStatus().name());
        return snapshot;
    }

    private boolean uploaded(PhotoAsset asset) {
        return asset != null && asset.status() == PhotoAssetStatus.UPLOADED;
    }

    private Map<String, Object> requestPayload(
            InspectionReport report,
            ReportPreflightReviewRun run,
            boolean aiReviewPlanned
    ) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("reportId", report.id());
        payload.put("reviewRunId", run.id());
        payload.put("aiReviewPlanned", aiReviewPlanned);
        if (aiReviewPlanned) {
            payload.put("harnessRunId", run.harnessRunId());
            payload.put("aiProviderCode", run.aiProviderCode());
            payload.put("aiModelId", run.aiModelId());
        }
        return payload;
    }
}
