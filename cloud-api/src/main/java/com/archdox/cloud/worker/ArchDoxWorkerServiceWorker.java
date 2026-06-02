package com.archdox.cloud.worker;

import com.archdox.cloud.global.event.ArchDoxRuntimeConfiguration;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.worker.DuplicatePolicy;
import org.springframework.stereotype.Component;

@Component
public class ArchDoxWorkerServiceWorker {
    private final Engine engine;

    public ArchDoxWorkerServiceWorker(Engine engine) {
        this.engine = engine;
    }

    public void submit(Flow flow) {
        engine.worker(ArchDoxRuntimeConfiguration.ARCHDOX_WORKER_SERVICE_WORKER)
                .submit(flow, DuplicatePolicy.REJECT);
    }
}
