package com.archdox.cloud.reportai.flow;

import com.archdox.cloud.global.event.ArchDoxRuntimeConfiguration;
import com.archdox.cloud.global.flow.FlowerFlowAsyncCompletionService;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Component;

@Component
public class ReportPreflightLegalReviewAiWorker {
    private final FlowerFlowAsyncCompletionService flowCompletionService;

    public ReportPreflightLegalReviewAiWorker(FlowerFlowAsyncCompletionService flowCompletionService) {
        this.flowCompletionService = flowCompletionService;
    }

    public void submit(AiHarnessFlow flow) {
        flowCompletionService.submit(ArchDoxRuntimeConfiguration.AI_HARNESS_WORKER, flow.flow());
    }

    public CompletableFuture<Boolean> submitAndTrackAsync(AiHarnessFlow flow, Duration timeout) {
        return flowCompletionService.submitAndTrackTerminal(
                ArchDoxRuntimeConfiguration.AI_HARNESS_WORKER,
                flow.flow(),
                timeout);
    }
}
