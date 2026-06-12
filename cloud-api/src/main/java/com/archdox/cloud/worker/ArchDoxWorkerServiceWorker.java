package com.archdox.cloud.worker;

import com.archdox.cloud.global.event.ArchDoxRuntimeConfiguration;
import com.archdox.cloud.global.flow.FlowerFlowAsyncCompletionService;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.worker.DuplicatePolicy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ArchDoxWorkerServiceWorker {
    private final Engine engine;
    private final FlowerFlowAsyncCompletionService flowCompletionService;

    @Autowired
    public ArchDoxWorkerServiceWorker(Engine engine, FlowerFlowAsyncCompletionService flowCompletionService) {
        this.engine = engine;
        this.flowCompletionService = flowCompletionService;
    }

    protected ArchDoxWorkerServiceWorker(Engine engine) {
        this(engine, null);
    }

    public void submit(Flow flow) {
        engine.worker(ArchDoxRuntimeConfiguration.ARCHDOX_WORKER_SERVICE_WORKER)
                .submit(flow, DuplicatePolicy.REJECT);
    }

    public CompletableFuture<Boolean> submitAndTrackAsync(Flow flow, Duration timeout) {
        if (flowCompletionService == null) {
            throw new IllegalStateException("Flower flow async completion service is not configured");
        }
        return flowCompletionService.submitAndTrackTerminal(
                ArchDoxRuntimeConfiguration.ARCHDOX_WORKER_SERVICE_WORKER,
                flow,
                timeout);
    }
}
