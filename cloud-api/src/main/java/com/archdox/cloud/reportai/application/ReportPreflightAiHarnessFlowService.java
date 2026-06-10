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
import java.util.Arrays;
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
        var legalReferences = legalReferences(findings);
        var photoEvidenceStatus = photoEvidenceStatusService.evaluate(report);
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
                findingSummaries(findings),
                legalReferences,
                legalReviewContext(findings, legalReferences),
                REVIEW_MODE_SOURCE_BACKED_LEGAL_DRY_RUN);
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

    private List<Map<String, Object>> legalReferences(List<ReportPreflightReviewFinding> findings) {
        return nullToEmpty(findings).stream()
                .flatMap(finding -> parseLegalReferenceDetails(finding.attributesJson().get("legalReferenceDetails")).stream())
                .collect(Collectors.toMap(
                        reference -> text(reference.get("referenceId")),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new))
                .values()
                .stream()
                .toList();
    }

    private Map<String, Object> legalReviewContext(
            List<ReportPreflightReviewFinding> findings,
            List<Map<String, Object>> legalReferences
    ) {
        var context = new LinkedHashMap<String, Object>();
        context.put("purpose", "SOURCE_BACKED_LEGAL_RISK_REVIEW_DRY_RUN");
        context.put("mode", REVIEW_MODE_SOURCE_BACKED_LEGAL_DRY_RUN);
        context.put("sourceBackedOnly", true);
        context.put("legalReferenceCount", legalReferences.size());
        context.put("legalFindings", nullToEmpty(findings).stream()
                .filter(finding -> finding.code().contains("LEGAL")
                        || !text(finding.attributesJson().get("legalReferences")).isBlank()
                        || !text(finding.attributesJson().get("legalReferenceDetails")).isBlank())
                .map(finding -> Map.of(
                        "source", finding.source(),
                        "code", finding.code(),
                        "severity", finding.severity(),
                        "location", finding.location() == null ? "" : finding.location(),
                        "message", finding.message(),
                        "evidence", finding.evidence() == null ? "" : finding.evidence(),
                        "nextActions", csvList(finding.attributesJson().get("engine.nextActions"))))
                .toList());
        context.put("instructions", List.of(
                "Use only sourceBackedLegalReferences as legal anchors.",
                "Treat output as draft review findings for human approval.",
                "Do not modify legal corpus, report data, or generation state."));
        return Map.copyOf(context);
    }

    private List<Map<String, Object>> parseLegalReferenceDetails(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(this::legalReferenceDetail)
                .filter(reference -> !text(reference.get("referenceId")).isBlank())
                .toList();
    }

    private Map<String, Object> legalReferenceDetail(String line) {
        var parts = line.split("\\t", -1);
        var reference = new LinkedHashMap<String, Object>();
        reference.put("referenceId", part(parts, 0));
        reference.put("label", part(parts, 1));
        reference.put("resolutionSource", part(parts, 2));
        reference.put("bindingScope", part(parts, 3));
        reference.put("bindingKey", part(parts, 4));
        reference.put("relevance", part(parts, 5));
        reference.put("catalogCode", part(parts, 6));
        reference.put("catalogVersion", part(parts, 7));
        reference.put("checklistItemCode", part(parts, 8));
        return Map.copyOf(reference);
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

    private List<String> csvList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String part(String[] parts, int index) {
        return index >= 0 && index < parts.length ? parts[index].trim() : "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
