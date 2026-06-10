package com.archdox.cloud.worker.application;

import com.archdox.cloud.document.application.DocumentGenerationRequestService;
import com.archdox.cloud.document.domain.DocumentWorkerType;
import com.archdox.cloud.document.dto.CreateDocumentJobRequest;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.reportai.application.ReportPreflightReviewService;
import com.archdox.cloud.reportai.flow.ReportPreflightReviewWorker;
import com.archdox.document.OutputFormat;
import com.archdox.worker.application.ArchDoxWorkerExecutionContext;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ArchDoxWorkerDocumentActionService {
    private final ReportPreflightReviewService preflightReviewService;
    private final ReportPreflightReviewWorker preflightReviewWorker;
    private final DocumentGenerationRequestService documentGenerationRequestService;

    public ArchDoxWorkerDocumentActionService(
            ReportPreflightReviewService preflightReviewService,
            ReportPreflightReviewWorker preflightReviewWorker,
            DocumentGenerationRequestService documentGenerationRequestService
    ) {
        this.preflightReviewService = preflightReviewService;
        this.preflightReviewWorker = preflightReviewWorker;
        this.documentGenerationRequestService = documentGenerationRequestService;
    }

    @Transactional
    public PreflightReviewActionResult runPreflightReview(ArchDoxWorkerExecutionContext context) {
        var officeId = requireOfficeId(context);
        var userId = requireUserId(context);
        var reportId = requireReportId(context, "Report must be selected before running preflight review");
        var submission = withOfficeContext(
                officeId,
                () -> preflightReviewService.requestReview(reportId, workerPrincipal(userId)));
        registerAfterCommit(() -> preflightReviewWorker.submit(submission.flow()));
        var response = submission.response();
        return new PreflightReviewActionResult(
                response.reportId(),
                response.id(),
                response.status(),
                response.reportRevision());
    }

    @Transactional
    public DocumentGenerationActionResult requestDocumentGeneration(ArchDoxWorkerExecutionContext context) {
        var officeId = requireOfficeId(context);
        var userId = requireUserId(context);
        var reportId = requireReportId(context, "Report must be selected before requesting document generation");
        var payload = context.action().payload();
        var outputFormat = outputFormatValue(payload.get("outputFormat"));
        var workerType = documentWorkerTypeValue(payload.get("workerType"));
        var job = withOfficeContext(
                officeId,
                () -> documentGenerationRequestService.request(
                        reportId,
                        new CreateDocumentJobRequest(outputFormat, workerType, null, null),
                        workerPrincipal(userId)));
        return new DocumentGenerationActionResult(
                job.reportId(),
                job.id(),
                job.status().name(),
                job.progressStep().name(),
                job.progressPercent(),
                job.outputFormat().name(),
                job.workerType().name());
    }

    private Long requireOfficeId(ArchDoxWorkerExecutionContext context) {
        var officeId = context.request().context().officeId();
        if (officeId == null) {
            throw new BadRequestException("Worker action requires officeId");
        }
        return officeId;
    }

    private Long requireUserId(ArchDoxWorkerExecutionContext context) {
        var userId = context.request().context().userId();
        if (userId == null) {
            throw new BadRequestException("Worker action requires userId");
        }
        return userId;
    }

    private Long requireReportId(ArchDoxWorkerExecutionContext context, String message) {
        var reportId = context.request().context().reportId();
        if (reportId == null) {
            reportId = longValue(context.action().payload().get("reportId"));
        }
        if (reportId == null) {
            throw new BadRequestException(message);
        }
        return reportId;
    }

    private OutputFormat outputFormatValue(Object value) {
        var normalized = defaultString(value, OutputFormat.DOCX.name()).trim().toUpperCase(Locale.ROOT);
        try {
            return OutputFormat.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid output format: " + value);
        }
    }

    private DocumentWorkerType documentWorkerTypeValue(Object value) {
        var normalized = trimToNull(stringValue(value));
        if (normalized == null) {
            return null;
        }
        try {
            return DocumentWorkerType.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid document worker type: " + value);
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.parseLong(string);
        }
        return null;
    }

    private String defaultString(Object value, String fallback) {
        var normalized = trimToNull(stringValue(value));
        return normalized == null ? fallback : normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private UserPrincipal workerPrincipal(Long userId) {
        return new UserPrincipal(userId, "archdox-worker@local");
    }

    private void registerAfterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    private <T> T withOfficeContext(Long officeId, Supplier<T> supplier) {
        var previous = OfficeContext.currentOfficeIdOrNull();
        OfficeContext.set(officeId);
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                OfficeContext.clear();
            } else {
                OfficeContext.set(previous);
            }
        }
    }

    public record PreflightReviewActionResult(
            Long reportId,
            Long preflightRunId,
            String preflightStatus,
            int reportRevision
    ) {
        public Map<String, Object> toPayload() {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("reportId", reportId);
            payload.put("preflightRunId", preflightRunId);
            payload.put("preflightStatus", preflightStatus);
            payload.put("reportRevision", reportRevision);
            return Map.copyOf(payload);
        }
    }

    public record DocumentGenerationActionResult(
            Long reportId,
            Long documentJobId,
            String documentJobStatus,
            String documentJobProgressStep,
            int documentJobProgressPercent,
            String outputFormat,
            String workerType
    ) {
        public Map<String, Object> toPayload() {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("reportId", reportId);
            payload.put("documentJobId", documentJobId);
            payload.put("documentJobStatus", documentJobStatus);
            payload.put("documentJobProgressStep", documentJobProgressStep);
            payload.put("documentJobProgressPercent", documentJobProgressPercent);
            payload.put("outputFormat", outputFormat);
            payload.put("workerType", workerType);
            return Map.copyOf(payload);
        }
    }
}
