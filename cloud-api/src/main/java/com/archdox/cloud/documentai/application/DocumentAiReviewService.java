package com.archdox.cloud.documentai.application;

import com.archdox.cloud.aipolicy.application.AiFeature;
import com.archdox.cloud.aipolicy.application.AiModelCallMetadata;
import com.archdox.cloud.aipolicy.application.AiPolicyExecutionService;
import com.archdox.cloud.document.domain.DocumentJobStatus;
import com.archdox.cloud.document.infra.DocumentArtifactRepository;
import com.archdox.cloud.document.infra.DocumentJobRepository;
import com.archdox.cloud.documentai.domain.DocumentAiReviewRun;
import com.archdox.cloud.documentai.dto.DocumentAiReviewFindingResponse;
import com.archdox.cloud.documentai.dto.DocumentAiReviewRunResponse;
import com.archdox.cloud.documentai.flow.DocumentReviewFlowFactory;
import com.archdox.cloud.documentai.flow.DocumentReviewRequest;
import com.archdox.cloud.documentai.infra.DocumentAiReviewFindingRepository;
import com.archdox.cloud.documentai.infra.DocumentAiReviewRunRepository;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.documentai.DocumentQaArtifactSummary;
import com.archdox.documentai.DocumentQaHarnessFactory;
import com.archdox.documentai.DocumentQaInput;
import com.archdox.documentai.DocumentQaResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlowFactory;
import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.spec.AiHarnessSpec;
import io.github.parkkevinsb.flower.ai.harness.spi.TraceListener;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentAiReviewService {
    private final DocumentJobRepository documentJobRepository;
    private final DocumentArtifactRepository documentArtifactRepository;
    private final InspectionReportRepository reportRepository;
    private final DocumentAiReviewRunRepository runRepository;
    private final DocumentAiReviewFindingRepository findingRepository;
    private final DocumentAiReviewRunStore runStore;
    private final DocumentAiReviewFindingSink findingSink;
    private final DocumentReviewFlowFactory documentReviewFlowFactory;
    private final DocumentAiReviewProperties properties;
    private final AiPolicyExecutionService aiPolicyExecutionService;
    private final AiModelGateway gateway;
    private final ObjectMapper objectMapper;
    private final OperationEventService operationEventService;
    private final TraceListener aiHarnessTraceListener;

    public DocumentAiReviewService(
            DocumentJobRepository documentJobRepository,
            DocumentArtifactRepository documentArtifactRepository,
            InspectionReportRepository reportRepository,
            DocumentAiReviewRunRepository runRepository,
            DocumentAiReviewFindingRepository findingRepository,
            DocumentAiReviewRunStore runStore,
            DocumentAiReviewFindingSink findingSink,
            DocumentReviewFlowFactory documentReviewFlowFactory,
            DocumentAiReviewProperties properties,
            AiPolicyExecutionService aiPolicyExecutionService,
            AiModelGateway gateway,
            ObjectMapper objectMapper,
            OperationEventService operationEventService,
            TraceListener aiHarnessTraceListener
    ) {
        this.documentJobRepository = documentJobRepository;
        this.documentArtifactRepository = documentArtifactRepository;
        this.reportRepository = reportRepository;
        this.runRepository = runRepository;
        this.findingRepository = findingRepository;
        this.runStore = runStore;
        this.findingSink = findingSink;
        this.documentReviewFlowFactory = documentReviewFlowFactory;
        this.properties = properties;
        this.aiPolicyExecutionService = aiPolicyExecutionService;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.operationEventService = operationEventService;
        this.aiHarnessTraceListener = aiHarnessTraceListener;
    }

    @Transactional
    public DocumentAiReviewSubmission requestDocumentQaReview(Long documentJobId, UserPrincipal principal) {
        if (!properties.isEnabled()) {
            throw new BadRequestException("Document AI review is disabled");
        }
        var officeId = OfficeContext.requireCurrentOfficeId();
        var job = documentJobRepository.findByIdAndOfficeId(documentJobId, officeId)
                .orElseThrow(() -> new NotFoundException("Document job not found"));
        if (job.status() != DocumentJobStatus.GENERATED) {
            throw new BadRequestException("Document AI review requires a generated document job");
        }
        var report = reportRepository.findByIdAndOfficeId(job.reportId(), officeId)
                .orElseThrow(() -> new NotFoundException("Inspection report not found"));
        var aiPlan = aiPolicyExecutionService.requireAllowed(
                officeId,
                principal == null ? null : principal.userId(),
                AiFeature.DOCUMENT_REVIEW);
        AiHarnessSpec<DocumentQaInput, DocumentQaResult> spec =
                new DocumentQaHarnessFactory(objectMapper).spec(
                        findingSink,
                        runStore,
                        new io.github.parkkevinsb.flower.ai.harness.refine.MaxAttemptsRefinePolicy(2),
                        aiHarnessTraceListener);
        var overrides = AiHarnessFlowFactory.RunOverrides.builder()
                .modelId(aiPlan.modelId())
                .providerOptions(AiModelCallMetadata.options(
                        officeId,
                        aiPlan.userId(),
                        AiFeature.DOCUMENT_REVIEW.name(),
                        "document-ai-review",
                        "document-job:" + job.id() + ":document-ai-review",
                        "DOCUMENT_JOB",
                        job.id(),
                        Map.of("archdox.reportId", job.reportId()),
                        aiPlan.maxOutputTokens()))
                .build();
        var flow = new AiHarnessFlowFactory<>(gateway, spec, Instant::now)
                .createFlow(input(job, report.reportType(), report.title()), overrides);
        var run = runRepository.saveAndFlush(new DocumentAiReviewRun(
                officeId,
                job.id(),
                job.reportId(),
                flow.context().runId().value(),
                flow.context().harnessId(),
                flow.context().promptVersion(),
                flow.context().status(),
                principal == null ? null : principal.userId(),
                OffsetDateTime.now()));
        operationEventService.record(
                officeId,
                OperationEventSeverity.INFO,
                "DOCUMENT_AI_REVIEW_REQUESTED",
                "document-ai-review",
                "document-ai-review-run:" + run.id(),
                "DOCUMENT_AI_REVIEW_RUN",
                run.id(),
                principal == null ? null : principal.userId(),
                null,
                "Document AI review run requested.",
                Map.of(
                        "documentJobId", job.id(),
                        "reportId", job.reportId(),
                        "harnessRunId", run.harnessRunId(),
                        "aiProviderCode", aiPlan.provider().providerCode(),
                        "aiProviderType", aiPlan.provider().providerType().name(),
                        "aiModelId", aiPlan.modelId().asString()));
        var request = new DocumentReviewRequest(
                officeId,
                job.id(),
                job.reportId(),
                run.id(),
                run.harnessRunId(),
                principal == null ? null : principal.userId());
        var documentReviewFlow = documentReviewFlowFactory.create(request, flow);
        return new DocumentAiReviewSubmission(toResponse(run), documentReviewFlow);
    }

    @Transactional(readOnly = true)
    public List<DocumentAiReviewRunResponse> listRuns(Long documentJobId) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        requireJob(officeId, documentJobId);
        return runRepository.findByOfficeIdAndDocumentJobIdOrderByRequestedAtDesc(officeId, documentJobId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentAiReviewFindingResponse> listFindings(Long documentJobId, Long runId) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        runRepository.findByIdAndOfficeIdAndDocumentJobId(runId, officeId, documentJobId)
                .orElseThrow(() -> new NotFoundException("Document AI review run not found"));
        return findingRepository.findByOfficeIdAndReviewRunIdOrderByIdAsc(officeId, runId).stream()
                .map(finding -> new DocumentAiReviewFindingResponse(
                        finding.id(),
                        finding.code(),
                        finding.severity(),
                        finding.location(),
                        finding.message(),
                        finding.evidence(),
                        finding.attributesJson(),
                        finding.createdAt()))
                .toList();
    }

    private DocumentQaInput input(com.archdox.cloud.document.domain.DocumentJob job, String reportType, String title) {
        var artifacts = documentArtifactRepository.findByDocumentJobIdOrderById(job.id()).stream()
                .map(artifact -> new DocumentQaArtifactSummary(
                        artifact.artifactType().name(),
                        artifact.fileName(),
                        artifact.mimeType(),
                        artifact.bytes()))
                .toList();
        return new DocumentQaInput(
                String.valueOf(job.officeId()),
                String.valueOf(job.id()),
                String.valueOf(job.reportId()),
                reportType,
                title,
                job.outputFormat().name(),
                job.inputSnapshotJson(),
                artifacts,
                "");
    }

    private void requireJob(Long officeId, Long documentJobId) {
        documentJobRepository.findByIdAndOfficeId(documentJobId, officeId)
                .orElseThrow(() -> new NotFoundException("Document job not found"));
    }

    private DocumentAiReviewRunResponse toResponse(DocumentAiReviewRun run) {
        return new DocumentAiReviewRunResponse(
                run.id(),
                run.officeId(),
                run.documentJobId(),
                run.reportId(),
                run.harnessRunId(),
                run.harnessId(),
                run.promptVersion().asString(),
                run.status().name(),
                run.attempt(),
                run.terminalReason(),
                run.requestedBy(),
                run.requestedAt(),
                run.updatedAt(),
                run.completedAt());
    }
}
