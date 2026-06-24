package com.archdox.cloud.aiharness.flow.step;

import com.archdox.cloud.aiharness.application.AiObservabilityRetentionService;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiObservabilityRetentionMonitorStep extends Step {
    private static final Logger log = LoggerFactory.getLogger(AiObservabilityRetentionMonitorStep.class);
    private static final int CHECK = 0;
    private static final int WAIT = 100;

    private final AiObservabilityRetentionService retentionService;

    public AiObservabilityRetentionMonitorStep(AiObservabilityRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        if (!retentionService.enabled()) {
            return StepResult.done();
        }
        return switch (ctx.stepNo()) {
            case CHECK -> check(ctx);
            case WAIT -> waitUntilNextCheck(ctx);
            default -> StepResult.fail(new IllegalStateException(
                    "Unknown AI observability retention monitor stepNo: " + ctx.stepNo()));
        };
    }

    private StepResult check(StepContext ctx) {
        try {
            var result = retentionService.purgeExpired(
                    OffsetDateTime.ofInstant(Instant.ofEpochMilli(ctx.clock().currentTimeMillis()), ZoneOffset.UTC));
            if (result.deletedTraceEvents() > 0 || result.deletedModelCallLogs() > 0) {
                log.info(
                        "AI observability retention purged {} trace events and {} model call logs before {}",
                        result.deletedTraceEvents(),
                        result.deletedModelCallLogs(),
                        result.cutoff());
            }
        } catch (Exception ex) {
            log.warn("AI observability retention monitor check failed", ex);
        }
        ctx.startTimeout(retentionService.checkIntervalMs());
        ctx.setStepNo(WAIT);
        return StepResult.stay();
    }

    private StepResult waitUntilNextCheck(StepContext ctx) {
        if (ctx.timedOut()) {
            ctx.setStepNo(CHECK);
        }
        return StepResult.stay();
    }
}
