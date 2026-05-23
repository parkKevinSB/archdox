package com.archdox.cloud.document.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.agent.application.ArchDoxAgentCommandService;
import com.archdox.cloud.document.application.DocumentGenerationException;
import com.archdox.cloud.document.application.DocumentGenerationProperties;
import com.archdox.cloud.document.application.DocumentJobService;
import com.archdox.cloud.document.domain.DocumentWorkerType;
import com.archdox.cloud.document.event.DocumentGenerationFailedEvent;
import com.archdox.cloud.document.event.DocumentGenerationRequested;
import com.archdox.cloud.document.event.DocumentRenderCommandAckedEvent;
import com.archdox.cloud.document.event.DocumentRenderCommandCompletedEvent;
import io.github.parkkevinsb.bloom.LocalEventBus;
import io.github.parkkevinsb.flower.bloom.BloomEventBus;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DocumentGenerationFlowFactoryTest {
    @Test
    void runsValidationAndCloudRenderSteps() {
        var service = mock(DocumentJobService.class);
        var commands = mock(ArchDoxAgentCommandService.class);
        var worker = workerWith(LocalEventBus.create());
        worker.submit(new DocumentGenerationFlowFactory(service, commands, Runnable::run, properties()).create(event(DocumentWorkerType.CLOUD)));

        tick(worker, 8);

        verify(service).validateJobReady(10L, 700L);
        verify(service).generateCloudDocument(10L, 700L);
    }

    @Test
    void retriesFailedRenderStepAfterBackoff() {
        var service = mock(DocumentJobService.class);
        var commands = mock(ArchDoxAgentCommandService.class);
        doThrow(new DocumentGenerationException("TEMPORARY", "temporary"))
                .doNothing()
                .when(service)
                .generateCloudDocument(10L, 700L);
        var clock = new ManualClock();
        var bloom = LocalEventBus.create();
        var properties = properties();
        properties.setMaxAttempts(2);
        properties.setRetryBaseDelayMs(50);
        properties.setRetryMaxDelayMs(50);
        var worker = workerWith(bloom, clock);

        worker.submit(new DocumentGenerationFlowFactory(service, commands, Runnable::run, properties).create(event(DocumentWorkerType.CLOUD)));
        tick(worker, 20);
        verify(service, times(1)).generateCloudDocument(10L, 700L);

        clock.advance(50);
        tick(worker, 20);

        verify(service, times(2)).generateCloudDocument(10L, 700L);
    }

    @Test
    void publishesFailureEventAfterRetryBudgetIsExhausted() {
        var service = mock(DocumentJobService.class);
        var commands = mock(ArchDoxAgentCommandService.class);
        doThrow(new DocumentGenerationException("TEMPLATE_ERROR", "missing field"))
                .when(service)
                .generateCloudDocument(10L, 700L);
        var clock = new ManualClock();
        var bloom = LocalEventBus.create();
        var published = new ArrayList<DocumentGenerationFailedEvent>();
        bloom.subscribe(DocumentGenerationFailedEvent.class, published::add);
        var properties = properties();
        properties.setMaxAttempts(2);
        properties.setRetryBaseDelayMs(50);
        properties.setRetryMaxDelayMs(50);
        var worker = workerWith(bloom, clock);

        worker.submit(new DocumentGenerationFlowFactory(service, commands, Runnable::run, properties).create(event(DocumentWorkerType.CLOUD)));
        tick(worker, 20);
        clock.advance(50);
        tick(worker, 20);

        assertEquals(1, published.size());
        assertEquals(700L, published.get(0).documentJobId());
        assertEquals("render-cloud-document", published.get(0).stepId());
        assertEquals(2, published.get(0).attempt());
    }

    @Test
    void archDoxAgentRenderWaitsForAckAndCompletionEvents() {
        var service = mock(DocumentJobService.class);
        var commands = mock(ArchDoxAgentCommandService.class);
        when(service.buildArchDoxAgentRenderPayload(10L, 700L)).thenReturn(Map.of(
                "documentJobId", 700L,
                "officeId", 10L,
                "reportId", 1000L));
        when(commands.enqueueDocumentRender(10L, 700L, Map.of(
                "documentJobId", 700L,
                "officeId", 10L,
                "reportId", 1000L), 1, 3))
                .thenReturn(Optional.of(99L));
        var bloom = LocalEventBus.create();
        var worker = workerWith(bloom);
        worker.submit(new DocumentGenerationFlowFactory(service, commands, Runnable::run, properties())
                .create(event(DocumentWorkerType.ARCHDOX_AGENT)));

        tick(worker, 8);

        verify(service).validateJobReady(10L, 700L);
        verify(service).markArchDoxAgentRenderDispatched(10L, 700L);
        verify(commands).enqueueDocumentRender(anyLong(), anyLong(), anyMap(), anyInt(), anyInt());

        bloom.publish(new DocumentRenderCommandAckedEvent(10L, 700L, 99L, OffsetDateTime.now()));
        tick(worker, 4);
        verify(service).markArchDoxAgentRenderAcked(10L, 700L);

        var result = Map.<String, Object>of("artifacts", List.of(Map.of(
                "artifactType", "DOCX",
                "storageKind", "ARCHDOX_AGENT",
                "storageRef", "documents/jobs/700/inspection-report-1000.docx",
                "fileName", "inspection-report-1000.docx",
                "bytes", 10L)));
        bloom.publish(new DocumentRenderCommandCompletedEvent(10L, 700L, 99L, result, OffsetDateTime.now()));
        tick(worker, 4);

        verify(service).completeArchDoxAgentDocument(10L, 700L, result);
    }

    private Worker workerWith(LocalEventBus bloom) {
        return workerWith(bloom, new ManualClock());
    }

    private Worker workerWith(LocalEventBus bloom, ManualClock clock) {
        var worker = Worker.builder("test").build();
        var engine = Engine.builder()
                .clock(clock)
                .eventBus(BloomEventBus.wrap(bloom))
                .worker(worker)
                .build();
        engine.attach();
        return worker;
    }

    private DocumentGenerationRequested event(DocumentWorkerType workerType) {
        return new DocumentGenerationRequested(10L, 1000L, 700L, workerType, OffsetDateTime.now());
    }

    private DocumentGenerationProperties properties() {
        var properties = new DocumentGenerationProperties();
        properties.setMaxAttempts(3);
        properties.setRetryBaseDelayMs(10);
        properties.setRetryMaxDelayMs(10);
        properties.setStepTimeoutMs(1000);
        return properties;
    }

    private void tick(Worker worker, int count) {
        for (int i = 0; i < count; i++) {
            worker.tickOnce();
        }
    }
}
