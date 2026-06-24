package com.archdox.cloud.reportai.application;

import com.archdox.cloud.aipolicy.application.AiFeature;
import com.archdox.cloud.aipolicy.application.AiModelCallMetadata;
import com.archdox.cloud.aipolicy.application.AiPolicyExecutionService;
import com.archdox.cloud.documentai.application.DocumentAiReviewProperties;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.photo.domain.Photo;
import com.archdox.cloud.photo.domain.PhotoAsset;
import com.archdox.cloud.photo.domain.PhotoAssetStatus;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.infra.PhotoAssetRepository;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewFinding;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewRun;
import com.archdox.documentai.ReportPreflightFindingSummary;
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
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ReportPreflightAiHarnessFlowService {
    public static final String REVIEW_MODE_SOURCE_BACKED_LEGAL_DRY_RUN = "SOURCE_BACKED_LEGAL_DRY_RUN";

    private final InspectionReportStepRepository stepRepository;
    private final PhotoRepository photoRepository;
    private final PhotoAssetRepository photoAssetRepository;
    private final DocumentAiReviewProperties aiReviewProperties;
    private final AiPolicyExecutionService aiPolicyExecutionService;
    private final AiModelGateway aiModelGateway;
    private final ReportPreflightAiReviewRunStore aiReviewRunStore;
    private final ReportPreflightAiReviewFindingSink aiReviewFindingSink;
    private final ReportPhotoEvidenceStatusService photoEvidenceStatusService;
    private final ObjectMapper objectMapper;
    private final TraceListener aiHarnessTraceListener;

    public ReportPreflightAiHarnessFlowService(
            InspectionReportStepRepository stepRepository,
            PhotoRepository photoRepository,
            PhotoAssetRepository photoAssetRepository,
            DocumentAiReviewProperties aiReviewProperties,
            AiPolicyExecutionService aiPolicyExecutionService,
            AiModelGateway aiModelGateway,
            ReportPreflightAiReviewRunStore aiReviewRunStore,
            ReportPreflightAiReviewFindingSink aiReviewFindingSink,
            ReportPhotoEvidenceStatusService photoEvidenceStatusService,
            ObjectMapper objectMapper,
            TraceListener aiHarnessTraceListener
    ) {
        this.stepRepository = stepRepository;
        this.photoRepository = photoRepository;
        this.photoAssetRepository = photoAssetRepository;
        this.aiReviewProperties = aiReviewProperties;
        this.aiPolicyExecutionService = aiPolicyExecutionService;
        this.aiModelGateway = aiModelGateway;
        this.aiReviewRunStore = aiReviewRunStore;
        this.aiReviewFindingSink = aiReviewFindingSink;
        this.photoEvidenceStatusService = photoEvidenceStatusService;
        this.objectMapper = objectMapper;
        this.aiHarnessTraceListener = aiHarnessTraceListener;
    }

    public boolean canCreate(InspectionReport report, Long userId) {
        return resolveAiPlan(report.officeId(), userId).isPresent();
    }

    public AiHarnessFlow create(InspectionReport report, ReportPreflightReviewRun run, List<ReportPreflightReviewFinding> findings) {
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
                                "archdox.reviewRunId", run.id(),
                                "archdox.reviewMode", REVIEW_MODE_SOURCE_BACKED_LEGAL_DRY_RUN),
                        aiPlan.get().maxOutputTokens()))
                .build();
        var flow = new AiHarnessFlowFactory<>(aiModelGateway, spec, Instant::now)
                .createFlow(input(report, findings), overrides);
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

    private ReportPreflightInput input(InspectionReport report, List<ReportPreflightReviewFinding> findings) {
        var photoEvidenceStatus = photoEvidenceStatusService.evaluate(report);
        var documentQaFindings = documentQaFindings(findings);
        return new ReportPreflightInput(
                String.valueOf(report.officeId()),
                String.valueOf(report.id()),
                report.reportType(),
                report.title(),
                report.status().name(),
                report.contentRevision(),
                reportSnapshot(report, photoEvidenceStatus),
                stepSnapshot(report),
                photoSnapshot(report),
                findingSummaries(documentQaFindings),
                List.of(),
                documentQaContext(),
                REVIEW_MODE_SOURCE_BACKED_LEGAL_DRY_RUN);
    }

    private List<ReportPreflightReviewFinding> documentQaFindings(List<ReportPreflightReviewFinding> findings) {
        return nullToEmpty(findings).stream()
                .filter(finding -> !isLegalContextFinding(finding))
                .toList();
    }

    private List<ReportPreflightFindingSummary> findingSummaries(List<ReportPreflightReviewFinding> findings) {
        return nullToEmpty(findings).stream()
                .map(finding -> new ReportPreflightFindingSummary(
                        finding.source(),
                        finding.code(),
                        finding.severity(),
                        finding.location(),
                        finding.message(),
                        finding.evidence(),
                        finding.attributesJson()))
                .toList();
    }

    private Map<String, Object> documentQaContext() {
        var context = new LinkedHashMap<String, Object>();
        context.put("purpose", "GENERAL_REPORT_PREFLIGHT_QA");
        context.put("mode", REVIEW_MODE_SOURCE_BACKED_LEGAL_DRY_RUN);
        context.put("sourceBackedOnly", false);
        context.put("legalReferenceCount", 0);
        context.put("legalFindings", List.of());
        context.put("instructions", List.of(
                "Run only general report QA.",
                "Do not perform source-backed legal review in this harness.",
                "Legal references are handled by the separate source-backed legal review harness."));
        return Map.copyOf(context);
    }

    private boolean isLegalContextFinding(ReportPreflightReviewFinding finding) {
        if (finding == null) {
            return false;
        }
        var code = text(finding.code()).toUpperCase(java.util.Locale.ROOT);
        if (code.startsWith("LEGAL_") || "LEGAL_CONTEXT".equalsIgnoreCase(text(finding.location()))) {
            return true;
        }
        var attributes = finding.attributesJson();
        return !text(attributes.get("legalReferences")).isBlank()
                || !text(attributes.get("legalReferenceDetails")).isBlank();
    }

    private Map<String, Object> reportSnapshot(
            InspectionReport report,
            ReportPhotoEvidenceStatus photoEvidenceStatus
    ) {
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
        snapshot.put("photoEvidenceStatus", photoEvidenceStatus.toMap());
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

    private List<ReportPreflightReviewFinding> nullToEmpty(List<ReportPreflightReviewFinding> findings) {
        return findings == null ? List.of() : findings;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
