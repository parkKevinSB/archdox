package com.archdox.cloud.platformops.flow;

import com.archdox.cloud.global.event.ArchDoxRuntimeConfiguration;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.worker.DuplicatePolicy;
import org.springframework.stereotype.Component;

@Component
public class PlatformOpsAiHarnessWorker {
    private final Engine engine;

    public PlatformOpsAiHarnessWorker(Engine engine) {
        this.engine = engine;
    }

    public void submit(AiHarnessFlow flow) {
        engine.worker(ArchDoxRuntimeConfiguration.AI_HARNESS_WORKER)
                .submit(flow.flow(), DuplicatePolicy.REJECT);
    }
}
