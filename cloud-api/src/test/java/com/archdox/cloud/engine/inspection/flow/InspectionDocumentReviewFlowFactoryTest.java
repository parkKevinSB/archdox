package com.archdox.cloud.engine.inspection.flow;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.engine.auth.application.EngineApiPrincipal;
import io.github.parkkevinsb.bloom.LocalEventBus;
import io.github.parkkevinsb.flower.bloom.BloomEventBus;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InspectionDocumentReviewFlowFactoryTest {
    @Test
    void inputGateSkipsNormalizeAndValidationWhenDateSelectionIsRequired() {
        var service = mock(InspectionDocumentReviewFlowService.class);
        var request = request("");
        when(service.requiresInputGateBeforeValidation(request)).thenReturn(true);

        var worker = worker();
        worker.submit(new InspectionDocumentReviewFlowFactory(service).create(request));
        tick(worker, 8);

        verify(service).createAndSubmitDocument(request);
        verify(service).extractAndSubmitFacts(request);
        verify(service).requiresInputGateBeforeValidation(request);
        verify(service, never()).normalize(request);
        verify(service, never()).runValidation(request);
        verify(service).complete(request);
    }

    @Test
    void inputGateAllowsNormalizeAndValidationWhenDateSelectionIsNotRequired() {
        var service = mock(InspectionDocumentReviewFlowService.class);
        var request = request("2021-01-07");
        when(service.requiresInputGateBeforeValidation(request)).thenReturn(false);

        var worker = worker();
        worker.submit(new InspectionDocumentReviewFlowFactory(service).create(request));
        tick(worker, 10);

        verify(service).createAndSubmitDocument(request);
        verify(service).extractAndSubmitFacts(request);
        verify(service).requiresInputGateBeforeValidation(request);
        verify(service).normalize(request);
        verify(service).runValidation(request);
        verify(service).complete(request);
    }

    private InspectionDocumentReviewRequest request(String targetDate) {
        return new InspectionDocumentReviewRequest(
                "req-test",
                new EngineApiPrincipal(1L, "test-key", 1L, null, List.of("ENGINE_REVIEW_SESSION"), 100),
                "project",
                "preflight",
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "daily-log.pdf",
                "daily log",
                targetDate,
                Map.of(),
                List.of(),
                new InspectionDocumentReviewState());
    }

    private Worker worker() {
        var worker = Worker.builder("test").build();
        var engine = Engine.builder()
                .clock(new ManualClock())
                .eventBus(BloomEventBus.wrap(LocalEventBus.create()))
                .worker(worker)
                .build();
        engine.attach();
        return worker;
    }

    private void tick(Worker worker, int count) {
        for (int i = 0; i < count; i++) {
            worker.tickOnce();
        }
    }
}
