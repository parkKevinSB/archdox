package com.archdox.cloud.documentai.application;

import com.archdox.cloud.document.infra.DocumentArtifactRepository;
import com.archdox.cloud.document.infra.DocumentJobRepository;
import com.archdox.cloud.documentai.domain.DocumentAiReviewFinding;
import com.archdox.cloud.documentai.flow.DocumentReviewRequest;
import com.archdox.cloud.documentai.infra.DocumentAiReviewFindingRepository;
import com.archdox.cloud.documentai.infra.DocumentAiReviewRunRepository;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStatus;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentReviewFlowService {
    private final DocumentJobRepository documentJobRepository;
    private final DocumentArtifactRepository artifactRepository;
    private final InspectionReportRepository reportRepository;
    private final DocumentAiReviewRunRepository runRepository;
    private final DocumentAiReviewFindingRepository findingRepository;
    private final DocumentDeterministicReviewValidator deterministicReviewValidator;
    private final OperationEventService operationEventService;

    public DocumentReviewFlowService(
            DocumentJobRepository documentJobRepository,
            DocumentArtifactRepository artifactRepository,
            InspectionReportRepository reportRepository,
            DocumentAiReviewRunRepository runRepository,
            DocumentAiReviewFindingRepository findingRepository,
            DocumentDeterministicReviewValidator deterministicReviewValidator,
            OperationEventService operationEventService
    ) {
        this.documentJobRepository = documentJobRepository;
        this.artifactRepository = artifactRepository;
        this.reportRepository = reportRepository;
        this.runRepository = runRepository;
        this.findingRepository = findingRepository;
        this.deterministicReviewValidator = deterministicReviewValidator;
        this.operationEventService = operationEventService;
    }

    @Transactional(readOnly = true)
    public void validateReviewContext(DocumentReviewRequest request) {
        documentJobRepository.findByIdAndOfficeId(request.documentJobId(), request.officeId())
                .orElseThrow(() -> new NotFoundException("Document job not found"));
        reportRepository.findByIdAndOfficeId(request.reportId(), request.officeId())
                .orElseThrow(() -> new NotFoundException("Inspection report not found"));
        runRepository.findByIdAndOfficeIdAndDocumentJobId(
                        request.reviewRunId(),
                        request.officeId(),
                        request.documentJobId())
                .orElseThrow(() -> new NotFoundException("Document AI review run not found"));
    }

    @Transactional
    public DeterministicDocumentReviewResult runDeterministicValidation(DocumentReviewRequest request) {
        var job = documentJobRepository.findByIdAndOfficeId(request.documentJobId(), request.officeId())
                .orElseThrow(() -> new NotFoundException("Document job not found"));
        var report = reportRepository.findByIdAndOfficeId(request.reportId(), request.officeId())
                .orElseThrow(() -> new NotFoundException("Inspection report not found"));
        var run = runRepository.findByIdAndOfficeIdAndDocumentJobId(
                        request.reviewRunId(),
                        request.officeId(),
                        request.documentJobId())
                .orElseThrow(() -> new NotFoundException("Document AI review run not found"));
        var result = deterministicReviewValidator.validate(
                job,
                report,
                artifactRepository.findByDocumentJobIdOrderById(request.documentJobId()));
        if (!result.findings().isEmpty()) {
            findingRepository.deleteByReviewRunId(request.reviewRunId());
            for (var finding : result.findings()) {
                findingRepository.save(new DocumentAiReviewFinding(
                        request.officeId(),
                        request.reviewRunId(),
                        request.documentJobId(),
                        request.reportId(),
                        finding.code(),
                        finding.severity(),
                        finding.location(),
                        finding.message(),
                        finding.evidence(),
                        finding.attributes(),
                        OffsetDateTime.now()));
            }
        }
        if (result.blocksAiReview()) {
            run.markSucceededWithoutHarness(
                    "DETERMINISTIC_VALIDATION_BLOCKED_AI_REVIEW",
                    OffsetDateTime.now());
        }
        operationEventService.record(
                request.officeId(),
                result.blocksAiReview() ? OperationEventSeverity.WARN : OperationEventSeverity.INFO,
                "DOCUMENT_REVIEW_DETERMINISTIC_VALIDATION",
                "document-review",
                workflowKey(request),
                "DOCUMENT_AI_REVIEW_RUN",
                request.reviewRunId(),
                request.requestedBy(),
                null,
                result.blocksAiReview()
                        ? "Deterministic document review found blocking issues and skipped AI review."
                        : "Deterministic document review passed and AI review may continue.",
                Map.of(
                        "documentJobId", request.documentJobId(),
                        "reportId", request.reportId(),
                        "reviewRunId", request.reviewRunId(),
                        "findingCount", result.findings().size(),
                        "blockingFindingCount", result.blockingFindingCount(),
                        "aiReviewSkipped", result.blocksAiReview()));
        return result;
    }

    @Transactional
    public void markHarnessSubmitted(DocumentReviewRequest request) {
        operationEventService.record(
                request.officeId(),
                OperationEventSeverity.INFO,
                "DOCUMENT_REVIEW_FLOW_HARNESS_SUBMITTED",
                "document-review",
                workflowKey(request),
                "DOCUMENT_AI_REVIEW_RUN",
                request.reviewRunId(),
                request.requestedBy(),
                null,
                "Document review flow submitted the Document QA harness.",
                payload(request));
    }

    @Transactional(readOnly = true)
    public boolean isHarnessTerminal(DocumentReviewRequest request) {
        return runRepository.findByIdAndOfficeIdAndDocumentJobId(
                        request.reviewRunId(),
                        request.officeId(),
                        request.documentJobId())
                .map(run -> isTerminal(run.status()))
                .orElseThrow(() -> new NotFoundException("Document AI review run not found"));
    }

    @Transactional
    public DocumentReviewSummary summarize(DocumentReviewRequest request) {
        var run = runRepository.findByIdAndOfficeIdAndDocumentJobId(
                        request.reviewRunId(),
                        request.officeId(),
                        request.documentJobId())
                .orElseThrow(() -> new NotFoundException("Document AI review run not found"));
        long findingCount = findingRepository.countByOfficeIdAndReviewRunId(
                request.officeId(),
                request.reviewRunId());
        var outcome = outcomeOf(run.status(), findingCount);
        operationEventService.record(
                request.officeId(),
                outcome == DocumentReviewOutcome.FAILED ? OperationEventSeverity.WARN : OperationEventSeverity.INFO,
                "DOCUMENT_REVIEW_FLOW_SUMMARIZED",
                "document-review",
                workflowKey(request),
                "DOCUMENT_AI_REVIEW_RUN",
                request.reviewRunId(),
                request.requestedBy(),
                null,
                "Document review flow summarized result as " + outcome.name() + ".",
                Map.of(
                        "documentJobId", request.documentJobId(),
                        "reportId", request.reportId(),
                        "reviewRunId", request.reviewRunId(),
                        "harnessRunId", request.harnessRunId(),
                        "harnessStatus", run.status().name(),
                        "findingCount", findingCount,
                        "outcome", outcome.name()));
        return new DocumentReviewSummary(outcome, run.status(), findingCount);
    }

    @Transactional
    public void complete(DocumentReviewRequest request) {
        operationEventService.record(
                request.officeId(),
                OperationEventSeverity.INFO,
                "DOCUMENT_REVIEW_FLOW_COMPLETED",
                "document-review",
                workflowKey(request),
                "DOCUMENT_AI_REVIEW_RUN",
                request.reviewRunId(),
                request.requestedBy(),
                null,
                "Document review flow completed.",
                payload(request));
    }

    private static DocumentReviewOutcome outcomeOf(AiHarnessRunStatus status, long findingCount) {
        if (status != AiHarnessRunStatus.SUCCEEDED) {
            return DocumentReviewOutcome.FAILED;
        }
        return findingCount == 0 ? DocumentReviewOutcome.PASSED : DocumentReviewOutcome.NEEDS_ATTENTION;
    }

    private static boolean isTerminal(AiHarnessRunStatus status) {
        return status == AiHarnessRunStatus.SUCCEEDED
                || status == AiHarnessRunStatus.FAILED
                || status == AiHarnessRunStatus.CANCELLED;
    }

    private static String workflowKey(DocumentReviewRequest request) {
        return "document-job:" + request.documentJobId() + ":review-run:" + request.reviewRunId();
    }

    private static Map<String, Object> payload(DocumentReviewRequest request) {
        return Map.of(
                "documentJobId", request.documentJobId(),
                "reportId", request.reportId(),
                "reviewRunId", request.reviewRunId(),
                "harnessRunId", request.harnessRunId());
    }
}
