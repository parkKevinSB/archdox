package com.archdox.cloud.reportai.flow;

import com.archdox.cloud.global.event.ArchDoxRuntimeConfiguration;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.worker.DuplicatePolicy;
import org.springframework.stereotype.Component;

@Component
public class ReportPreflightReviewWorker {
    private final Engine engine;

    public ReportPreflightReviewWorker(Engine engine) {
        this.engine = engine;
    }

    public void submit(Flow flow) {
        engine.worker(ArchDoxRuntimeConfiguration.REPORT_PREFLIGHT_REVIEW_WORKER)
                .submit(flow, DuplicatePolicy.REJECT);
    }
}
