package com.archdox.cloud.operation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.operation.domain.OperationEvent;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.operation.infra.OperationEventRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OperationEventServiceTest {
    @Mock
    OperationEventRepository repository;

    @AfterEach
    void clearOfficeContext() {
        OfficeContext.clear();
    }

    @Test
    void recordsNormalizedOperationEvent() {
        var service = new OperationEventService(repository);

        service.record(
                10L,
                null,
                " DOCUMENT_JOB_GENERATED ",
                " document-generation ",
                " document-job:700 ",
                " DOCUMENT_JOB ",
                700L,
                1L,
                " correlation-1 ",
                " Document job generated. ",
                Map.of("artifactCount", 1));

        var eventCaptor = ArgumentCaptor.forClass(OperationEvent.class);
        verify(repository).save(eventCaptor.capture());

        var event = eventCaptor.getValue();
        assertEquals(10L, event.officeId());
        assertEquals(OperationEventSeverity.INFO, event.severity());
        assertEquals("DOCUMENT_JOB_GENERATED", event.eventType());
        assertEquals("document-generation", event.workflowType());
        assertEquals("document-job:700", event.workflowKey());
        assertEquals("DOCUMENT_JOB", event.resourceType());
        assertEquals("700", event.resourceId());
        assertEquals(1L, event.actorUserId());
        assertEquals("correlation-1", event.correlationId());
        assertEquals("Document job generated.", event.message());
        assertEquals(1, event.payloadJson().get("artifactCount"));
    }

    @Test
    void listsOfficeEventsWithFiltersAndMaxLimit() {
        var service = new OperationEventService(repository);
        OfficeContext.set(10L);

        when(repository.searchOfficeEvents(
                eq(10L),
                eq("DOCUMENT_JOB_FAILED"),
                isNull(),
                isNull(),
                isNull(),
                eq("700"),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(new OperationEvent(
                        10L,
                        OperationEventSeverity.ERROR,
                        "DOCUMENT_JOB_FAILED",
                        null,
                        null,
                        "DOCUMENT_JOB",
                        "700",
                        null,
                        null,
                        "Document job failed.",
                        Map.of(),
                        OffsetDateTime.now())));

        var response = service.list(" DOCUMENT_JOB_FAILED ", " ", "", null, " 700 ", 999);

        var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).searchOfficeEvents(
                eq(10L),
                eq("DOCUMENT_JOB_FAILED"),
                isNull(),
                isNull(),
                isNull(),
                eq("700"),
                pageableCaptor.capture());

        assertEquals(200, pageableCaptor.getValue().getPageSize());
        assertEquals(1, response.size());
        assertEquals("DOCUMENT_JOB_FAILED", response.get(0).eventType());
    }
}
