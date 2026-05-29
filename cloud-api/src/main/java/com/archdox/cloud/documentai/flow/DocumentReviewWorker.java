package com.archdox.cloud.documentai.flow;

import com.archdox.cloud.global.event.ArchDoxRuntimeConfiguration;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.worker.DuplicatePolicy;
import org.springframework.stereotype.Component;

@Component
public class DocumentReviewWorker {
    private final Engine engine;

    public DocumentReviewWorker(Engine engine) {
        this.engine = engine;
    }

    public void submit(Flow flow) {
        engine.worker(ArchDoxRuntimeConfiguration.DOCUMENT_REVIEW_WORKER)
                .submit(flow, DuplicatePolicy.REJECT);
    }
}
