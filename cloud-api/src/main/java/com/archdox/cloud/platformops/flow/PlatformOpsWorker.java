package com.archdox.cloud.platformops.flow;

import com.archdox.cloud.global.event.ArchDoxRuntimeConfiguration;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.worker.DuplicatePolicy;
import org.springframework.stereotype.Component;

@Component
public class PlatformOpsWorker {
    private final Engine engine;

    public PlatformOpsWorker(Engine engine) {
        this.engine = engine;
    }

    public void submit(Flow flow) {
        engine.worker(ArchDoxRuntimeConfiguration.PLATFORM_OPS_WORKER)
                .submit(flow, DuplicatePolicy.REPLACE);
    }
}
