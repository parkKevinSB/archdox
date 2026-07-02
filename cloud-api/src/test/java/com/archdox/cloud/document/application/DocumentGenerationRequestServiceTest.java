package com.archdox.cloud.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.archdox.cloud.document.domain.DocumentJobProgressStep;
import com.archdox.cloud.document.domain.DocumentJobStatus;
import com.archdox.cloud.document.domain.DocumentWorkerType;
import com.archdox.cloud.document.dto.CreateDocumentJobRequest;
import com.archdox.cloud.document.dto.DocumentJobResponse;
import com.archdox.cloud.document.flow.DocumentGenerationFlowFactory;
import com.archdox.cloud.document.flow.DocumentGenerationWorker;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.document.OutputFormat;
import io.github.parkkevinsb.flower.core.flow.Flow;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentGenerationRequestServiceTest {
    private final DocumentJobService documentJobService = mock(DocumentJobService.class);
    private final DocumentGenerationFlowFactory flowFactory = mock(DocumentGenerationFlowFactory.class);
    private final DocumentGenerationWorker worker = mock(DocumentGenerationWorker.class);
    private final DocumentGenerationRequestService service = new DocumentGenerationRequestService(
            documentJobService,
            flowFactory,
            worker);

    @Test
    void idempotentRequestSubmitsFlowOnlyWhenJobWasCreated() {
        var response = response(700L);
        var flow = mock(Flow.class);
        when(documentJobService.createIdempotent(5L, request(), principal(), "worker-key"))
                .thenReturn(new DocumentJobService.DocumentJobCreateResult(response, true));
        when(flowFactory.create(any())).thenReturn(flow);

        var result = service.requestIdempotent(5L, request(), principal(), "worker-key");

        assertThat(result.id()).isEqualTo(700L);
        verify(flowFactory).create(any());
        verify(worker).submit(flow);
    }

    @Test
    void idempotentRequestReturnsExistingJobWithoutSubmittingFlowAgain() {
        var response = response(701L);
        when(documentJobService.createIdempotent(5L, request(), principal(), "worker-key"))
                .thenReturn(new DocumentJobService.DocumentJobCreateResult(response, false));

        var result = service.requestIdempotent(5L, request(), principal(), "worker-key");

        assertThat(result.id()).isEqualTo(701L);
        verify(flowFactory, never()).create(any());
        verifyNoInteractions(worker);
    }

    private CreateDocumentJobRequest request() {
        return new CreateDocumentJobRequest(OutputFormat.DOCX, null, null, null);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(1L, "worker@example.test");
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
