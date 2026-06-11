package com.archdox.cloud.legal.flow.step;

import com.archdox.cloud.legal.application.LegalSyncMonitorService;
import com.archdox.cloud.legal.application.LegalSyncProperties;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegalSyncMonitorStep extends Step {
    private static final Logger log = LoggerFactory.getLogger(LegalSyncMonitorStep.class);
    private static final int CHECK = 0;
    private static final int WAIT = 100;

    private final LegalSyncMonitorService monitorService;
    private final LegalSyncProperties properties;

    public LegalSyncMonitorStep(
            LegalSyncMonitorService monitorService,
            LegalSyncProperties properties
    ) {
        this.monitorService = monitorService;
        this.properties = properties;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        if (!properties.getMonitor().isEnabled()) {
            return StepResult.done();
        }
        return switch (ctx.stepNo()) {
            case CHECK -> check(ctx);
            case WAIT -> waitUntilNextCheck(ctx);
            default -> StepResult.fail(new IllegalStateException("Unknown legal sync monitor stepNo: " + ctx.stepNo()));
        };
    }

    private StepResult check(StepContext ctx) {
        try {
            var decision = monitorService.checkAndSubmitIfDue(
                    OffsetDateTime.ofInstant(Instant.ofEpochMilli(ctx.clock().currentTimeMillis()), ZoneOffset.UTC));
            if ("SUBMITTED".equals(decision.status())) {
                log.info("Legal sync monitor submitted run {} for due slot {}", decision.syncRunId(), decision.dueAt());
            }
        } catch (Exception ex) {
            log.warn("Legal sync monitor check failed", ex);
        }
        ctx.startTimeout(properties.getMonitor().safeCheckIntervalMs());
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
