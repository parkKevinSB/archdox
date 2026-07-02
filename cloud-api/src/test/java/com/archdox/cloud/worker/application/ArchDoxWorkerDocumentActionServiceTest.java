package com.archdox.cloud.worker.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.document.application.DocumentGenerationRequestService;
import com.archdox.cloud.document.domain.DocumentJobProgressStep;
import com.archdox.cloud.document.domain.DocumentJobStatus;
import com.archdox.cloud.document.domain.DocumentWorkerType;
import com.archdox.cloud.document.dto.CreateDocumentJobRequest;
import com.archdox.cloud.document.dto.DocumentJobResponse;
import com.archdox.cloud.reportai.application.ReportPreflightReviewService;
import com.archdox.cloud.reportai.flow.ReportPreflightReviewWorker;
import com.archdox.document.OutputFormat;
import com.archdox.worker.application.ArchDoxWorkerExecutionContext;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerActionOrigin;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import com.archdox.worker.domain.ArchDoxWorkerRequestContext;
import com.archdox.worker.domain.ArchDoxWorkerRequestSource;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArchDoxWorkerDocumentActionServiceTest {
    private final ReportPreflightReviewService preflightReviewService = mock(ReportPreflightReviewService.class);
    private final ReportPreflightReviewWorker preflightReviewWorker = mock(ReportPreflightReviewWorker.class);
    private final DocumentGenerationRequestService documentGenerationRequestService =
            mock(DocumentGenerationRequestService.class);
    private final ArchDoxWorkerDocumentActionService service = new ArchDoxWorkerDocumentActionService(
            preflightReviewService,
            preflightReviewWorker,
            documentGenerationRequestService);

    @Test
    void requestDocumentGenerationUsesProvidedWorkerIdempotencyKey() {
        var context = context(UUID.randomUUID(), Map.of(
                "reportId", 5L,
                "workerIdempotencyKey", "worker-key",
                "outputFormat", "DOCX"));
        when(documentGenerationRequestService.requestIdempotent(
                5L,
                new CreateDocumentJobRequest(OutputFormat.DOCX, null, null, null),
                new com.archdox.cloud.global.security.UserPrincipal(1L, "archdox-worker@local"),
                "worker-key"))
                .thenReturn(response(700L));

        var result = service.requestDocumentGeneration(context);

        assertThat(result.documentJobId()).isEqualTo(700L);
        verify(documentGenerationRequestService).requestIdempotent(
                5L,
                new CreateDocumentJobRequest(OutputFormat.DOCX, null, null, null),
                new com.archdox.cloud.global.security.UserPrincipal(1L, "archdox-worker@local"),
                "worker-key");
    }

    @Test
    void requestDocumentGenerationFallsBackToWorkerRequestIdempotencyKey() {
        var requestId = UUID.randomUUID();
        var context = context(requestId, Map.of("reportId", 5L));
        var expectedKey = "archdox-worker:" + requestId + ":REQUEST_DOCUMENT_GENERATION";
        when(documentGenerationRequestService.requestIdempotent(
                5L,
                new CreateDocumentJobRequest(OutputFormat.DOCX, null, null, null),
                new com.archdox.cloud.global.security.UserPrincipal(1L, "archdox-worker@local"),
                expectedKey))
                .thenReturn(response(701L));

        var result = service.requestDocumentGeneration(context);

        assertThat(result.documentJobId()).isEqualTo(701L);
        verify(documentGenerationRequestService).requestIdempotent(
                5L,
                new CreateDocumentJobRequest(OutputFormat.DOCX, null, null, null),
                new com.archdox.cloud.global.security.UserPrincipal(1L, "archdox-worker@local"),
                expectedKey);
    }

    private ArchDoxWorkerExecutionContext context(UUID requestId, Map<String, Object> payload) {
        var request = new ArchDoxWorkerRequest(
                requestId,
                ArchDoxWorkerRequestSource.UI,
                "test",
                new ArchDoxWorkerRequestContext(1L, 2L, 3L, 4L, 5L, null, "ko-KR"),
                Instant.now());
        var action = new ArchDoxWorkerAction(
                ArchDoxWorkerActionType.REQUEST_DOCUMENT_GENERATION,
                payload,
                "test",
                1.0d,
                ArchDoxWorkerActionOrigin.SYSTEM);
        return new ArchDoxWorkerExecutionContext(request, action);
    }

    private DocumentJobResponse response(Long id) {
        return new DocumentJobResponse(
                id,
                2L,
                5L,
                3L,
                1,
                DocumentJobStatus.REQUESTED,
                DocumentJobProgressStep.QUEUED,
                0,
                "queued",
                DocumentWorkerType.ARCHDOX_AGENT,
                OutputFormat.DOCX,
                null,
                null,
                OffsetDateTime.now(),
                null,
                null,
                List.of());
    }
}
