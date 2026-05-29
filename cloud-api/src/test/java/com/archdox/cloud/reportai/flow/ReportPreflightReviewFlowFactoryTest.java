package com.archdox.cloud.reportai.flow;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.reportai.application.ReportPreflightReviewFlowService;
import com.archdox.cloud.reportai.application.ReportPreflightValidationResult;
import io.github.parkkevinsb.bloom.LocalEventBus;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunId;
import io.github.parkkevinsb.flower.bloom.BloomEventBus;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportPreflightReviewFlowFactoryTest {
    @Test
    void runsDeterministicValidationAndCompletes() {
        var flowService = mock(ReportPreflightReviewFlowService.class);
        var aiReviewWorker = mock(ReportPreflightAiReviewWorker.class);
        var request = new ReportPreflightReviewRequest(10L, 100L, 200L, 300L);
        when(flowService.runDeterministicValidation(request))
                .thenReturn(new ReportPreflightValidationResult(List.of()));
        when(flowService.isAiHarnessTerminal(request)).thenReturn(true);

        var worker = worker();
        worker.submit(new ReportPreflightReviewFlowFactory(flowService, aiReviewWorker).create(request));
        tick(worker, 6);

        verify(flowService).validateContext(request);
        verify(flowService).runDeterministicValidation(request);
        verify(flowService).summarizeAiResult(request);
        verify(flowService).complete(request);
    }

    @Test
    void submitsAiHarnessAfterDeterministicValidationWhenAvailable() {
        var flowService = mock(ReportPreflightReviewFlowService.class);
        var aiReviewWorker = mock(ReportPreflightAiReviewWorker.class);
        var request = new ReportPreflightReviewRequest(10L, 100L, 200L, 300L);
        var aiFlow = aiHarnessFlow();
        when(flowService.runDeterministicValidation(request))
                .thenReturn(new ReportPreflightValidationResult(List.of()));
        when(flowService.canSubmitAiHarness(request)).thenReturn(true);
        when(flowService.isAiHarnessTerminal(request)).thenReturn(false, true);

        var worker = worker();
        worker.submit(new ReportPreflightReviewFlowFactory(flowService, aiReviewWorker).create(request, aiFlow));
        tick(worker, 8);

        verify(flowService).validateContext(request);
        verify(flowService).runDeterministicValidation(request);
        verify(aiReviewWorker).submit(aiFlow);
        verify(flowService).markAiHarnessSubmitted(request);
        verify(flowService).summarizeAiResult(request);
        verify(flowService).complete(request);
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

    private AiHarnessFlow aiHarnessFlow() {
        return new AiHarnessFlow(
                Flow.builder("test-ai-harness", "run-1")
                        .step("noop", new NoopStep())
                        .build(),
                new AiHarnessRunContext(
                        new AiHarnessRunId("run-1"),
                        "test-ai-harness",
                        new PromptVersion("test", "v1"),
                        Instant.now()));
    }

    private static final class NoopStep extends Step {
        @Override
        protected StepResult onTick(StepContext ctx) {
            return StepResult.done();
        }
    }
}
