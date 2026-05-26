package com.archdox.cloud.document.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.agent.application.ArchDoxAgentCommandService;
import com.archdox.cloud.document.application.DocumentGenerationProperties;
import com.archdox.cloud.document.application.DocumentJobService;
import com.archdox.cloud.document.domain.DocumentWorkerType;
import com.archdox.cloud.document.event.DocumentGenerationFailedEvent;
import com.archdox.cloud.document.event.DocumentGenerationRequested;
import com.archdox.cloud.document.event.DocumentRenderCommandAckedEvent;
import com.archdox.cloud.document.event.DocumentRenderCommandCompletedEvent;
import com.archdox.cloud.document.event.DocumentRenderCommandFailedEvent;
import io.github.parkkevinsb.bloom.LocalEventBus;
import io.github.parkkevinsb.flower.bloom.BloomEventBus;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DocumentGenerationFlowFactoryTest {
    @Test
    void archDoxAgentRenderDispatchesCommandEnvelopeAndStoresReturnedArtifacts() {
        var service = mock(DocumentJobService.class);
        var commands = mock(ArchDoxAgentCommandService.class);
        var payload = archDoxAgentRenderCommandPayload();
        when(service.buildArchDoxAgentRenderCommandPayload(10L, 700L)).thenReturn(payload);
        when(commands.enqueueDocumentRender(eq(10L), eq(700L), same(payload), eq(1), eq(3)))
                .thenReturn(Optional.of(99L));
        var bloom = LocalEventBus.create();
        var worker = workerWith(bloom);
        worker.submit(new DocumentGenerationFlowFactory(service, commands, Runnable::run, properties())
                .create(event(DocumentWorkerType.ARCHDOX_AGENT)));

        tick(worker, 8);

        verify(service).validateJobReady(10L, 700L);
        verify(service).markArchDoxAgentRenderDispatched(10L, 700L);
        verify(commands).enqueueDocumentRender(eq(10L), eq(700L), same(payload), eq(1), eq(3));

        bloom.publish(new DocumentRenderCommandAckedEvent(10L, 700L, 99L, OffsetDateTime.now()));
        tick(worker, 4);
        verify(service).markArchDoxAgentRenderAcked(10L, 700L);

        var result = Map.<String, Object>of("artifacts", List.of(Map.of(
                "artifactType", "DOCX",
                "storageKind", "ARCHDOX_AGENT",
                "storageRef", "documents/jobs/700/inspection-report-1000.docx",
                "fileName", "inspection-report-1000.docx",
                "bytes", 10L), Map.of(
                "artifactType", "PDF",
                "storageKind", "ARCHDOX_AGENT",
                "storageRef", "documents/jobs/700/inspection-report-1000.pdf",
                "fileName", "inspection-report-1000.pdf",
                "bytes", 20L)));
        bloom.publish(new DocumentRenderCommandCompletedEvent(10L, 700L, 99L, result, OffsetDateTime.now()));
        tick(worker, 4);

        verify(service).completeArchDoxAgentDocument(10L, 700L, result);
    }

    @Test
    void archDoxAgentRenderPublishesFailureWhenAgentReportsFailure() {
        var service = mock(DocumentJobService.class);
        var commands = mock(ArchDoxAgentCommandService.class);
        var payload = archDoxAgentRenderCommandPayload();
        when(service.buildArchDoxAgentRenderCommandPayload(10L, 700L)).thenReturn(payload);
        when(commands.enqueueDocumentRender(eq(10L), eq(700L), same(payload), eq(1), eq(3)))
                .thenReturn(Optional.of(99L));
        var bloom = LocalEventBus.create();
        var published = new ArrayList<DocumentGenerationFailedEvent>();
        bloom.subscribe(DocumentGenerationFailedEvent.class, published::add);
        var worker = workerWith(bloom);
        worker.submit(new DocumentGenerationFlowFactory(service, commands, Runnable::run, properties())
                .create(event(DocumentWorkerType.ARCHDOX_AGENT)));

        tick(worker, 8);
        bloom.publish(new DocumentRenderCommandFailedEvent(
                10L,
                700L,
                99L,
                "Renderer failed",
                Map.of("errorCode", "RENDERER_FAILED"),
                OffsetDateTime.now()));
        tick(worker, 4);

        verify(service).markGenerationFailed(
                10L,
                700L,
                "RENDERER_FAILED",
                "Renderer failed");
        assertEquals(1, published.size());
        assertEquals(700L, published.get(0).documentJobId());
        assertEquals("render-archdox-agent-document", published.get(0).stepId());
        assertEquals("Renderer failed", published.get(0).reason());
    }

    @Test
    void archDoxAgentRenderRetriesWhenAgentFailureIsRetryable() {
        var service = mock(DocumentJobService.class);
        var commands = mock(ArchDoxAgentCommandService.class);
        var payload = archDoxAgentRenderCommandPayload();
        when(service.buildArchDoxAgentRenderCommandPayload(10L, 700L)).thenReturn(payload);
        when(commands.enqueueDocumentRender(eq(10L), eq(700L), same(payload), eq(1), eq(2)))
                .thenReturn(Optional.of(99L));
        when(commands.enqueueDocumentRender(eq(10L), eq(700L), same(payload), eq(2), eq(2)))
                .thenReturn(Optional.of(100L));
        var clock = new ManualClock();
        var bloom = LocalEventBus.create();
        var properties = properties();
        properties.setMaxAttempts(2);
        properties.setRetryBaseDelayMs(50);
        properties.setRetryMaxDelayMs(50);
        var worker = workerWith(bloom, clock);
        worker.submit(new DocumentGenerationFlowFactory(service, commands, Runnable::run, properties)
                .create(event(DocumentWorkerType.ARCHDOX_AGENT)));

        tick(worker, 8);
        bloom.publish(new DocumentRenderCommandFailedEvent(
                10L,
                700L,
                99L,
                "AGENT_REMOTE_SERVICE_UNAVAILABLE",
                true,
                "Render package download failed with HTTP 503",
                Map.of("errorCode", "AGENT_REMOTE_SERVICE_UNAVAILABLE", "retryable", true),
                OffsetDateTime.now()));
        tick(worker, 4);

        clock.advance(50);
        tick(worker, 8);

        verify(commands).enqueueDocumentRender(eq(10L), eq(700L), same(payload), eq(1), eq(2));
        verify(commands).enqueueDocumentRender(eq(10L), eq(700L), same(payload), eq(2), eq(2));
        verify(service, never()).markGenerationFailed(eq(10L), eq(700L), eq("AGENT_REMOTE_SERVICE_UNAVAILABLE"), org.mockito.Mockito.any());
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

    private Map<String, Object> archDoxAgentRenderCommandPayload() {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("documentJobId", 700L);
        payload.put("officeId", 10L);
        payload.put("reportId", 1000L);
        payload.put("outputFormat", "DOCX");
        payload.put("renderPackageMethod", "GET");
        payload.put("renderPackageUrl", "/agent/api/v1/document-jobs/700/render-package");
        payload.put("resultStorageKind", "ARCHDOX_AGENT");
        return payload;
    }

    private void tick(Worker worker, int count) {
        for (int i = 0; i < count; i++) {
            worker.tickOnce();
        }
    }
}
