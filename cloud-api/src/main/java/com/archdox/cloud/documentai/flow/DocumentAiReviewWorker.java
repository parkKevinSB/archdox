package com.archdox.cloud.documentai.flow;

import com.archdox.cloud.global.event.ArchDoxRuntimeConfiguration;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.worker.DuplicatePolicy;
import org.springframework.stereotype.Component;

@Component
public class DocumentAiReviewWorker {
    private final Engine engine;

    public DocumentAiReviewWorker(Engine engine) {
        this.engine = engine;
    }

    public void submit(AiHarnessFlow flow) {
        engine.worker(ArchDoxRuntimeConfiguration.DOCUMENT_AI_REVIEW_WORKER)
                .submit(flow.flow(), DuplicatePolicy.REJECT);
    }
}
