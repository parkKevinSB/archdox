package com.archdox.cloud.documentai.flow;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.documentai.application.DeterministicDocumentReviewResult;
import com.archdox.cloud.documentai.application.DocumentReviewFlowService;
import com.archdox.cloud.documentai.application.DocumentReviewOutcome;
import com.archdox.cloud.documentai.application.DocumentReviewSummary;
import io.github.parkkevinsb.bloom.LocalEventBus;
import io.github.parkkevinsb.flower.bloom.BloomEventBus;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStatus;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentReviewFlowFactoryTest {
    @Test
    void skipsAiHarnessWhenDeterministicValidationAlreadyCompletedRun() {
        var service = mock(DocumentReviewFlowService.class);
        var aiReviewWorker = mock(DocumentAiReviewWorker.class);
        var aiHarnessFlow = mock(AiHarnessFlow.class);
        var request = new DocumentReviewRequest(10L, 200L, 300L, 400L, "harness-run", 500L);
        when(service.runDeterministicValidation(request))
                .thenReturn(new DeterministicDocumentReviewResult(List.of()));
        when(service.isHarnessTerminal(request)).thenReturn(true);
        when(service.summarize(request))
                .thenReturn(new DocumentReviewSummary(
                        DocumentReviewOutcome.NEEDS_ATTENTION,
                        AiHarnessRunStatus.SUCCEEDED,
                        1));

        var worker = worker();
        worker.submit(new DocumentReviewFlowFactory(service, aiReviewWorker).create(request, aiHarnessFlow));
        tick(worker, 8);

        verify(service).runDeterministicValidation(request);
        verify(aiReviewWorker, never()).submit(aiHarnessFlow);
        verify(service).summarize(request);
        verify(service).complete(request);
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
