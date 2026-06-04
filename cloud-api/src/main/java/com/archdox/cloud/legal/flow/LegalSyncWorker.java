package com.archdox.cloud.legal.flow;

import com.archdox.cloud.global.event.ArchDoxRuntimeConfiguration;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.worker.DuplicatePolicy;
import org.springframework.stereotype.Component;

@Component
public class LegalSyncWorker {
    private final Engine engine;

    public LegalSyncWorker(Engine engine) {
        this.engine = engine;
    }

    public void submit(Flow flow) {
        engine.worker(ArchDoxRuntimeConfiguration.LEGAL_SYNC_WORKER)
                .submit(flow, DuplicatePolicy.REPLACE);
    }
}
