package com.archdox.cloud.document.flow;

import com.archdox.cloud.global.event.ArchDoxRuntimeConfiguration;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.worker.DuplicatePolicy;
import org.springframework.stereotype.Component;

@Component
public class DocumentGenerationWorker {
    private final Engine engine;

    public DocumentGenerationWorker(Engine engine) {
        this.engine = engine;
    }

    public void submit(Flow flow) {
        engine.worker(ArchDoxRuntimeConfiguration.DOCUMENT_GENERATION_WORKER)
                .submit(flow, DuplicatePolicy.REPLACE);
    }
}
