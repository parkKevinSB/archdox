package com.archdox.cloud.monitoring.flow.step;

import com.archdox.cloud.monitoring.application.ServerRuntimeHealthService;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerRuntimeHealthMonitorStep extends Step {
    private static final Logger log = LoggerFactory.getLogger(ServerRuntimeHealthMonitorStep.class);
    private static final int CHECK = 0;
    private static final int WAIT = 100;

    private final ServerRuntimeHealthService healthService;

    public ServerRuntimeHealthMonitorStep(ServerRuntimeHealthService healthService) {
        this.healthService = healthService;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        return switch (ctx.stepNo()) {
            case CHECK -> check(ctx);
            case WAIT -> waitUntilNextCheck(ctx);
            default -> StepResult.fail(new IllegalStateException(
                    "Unknown server runtime health monitor stepNo: " + ctx.stepNo()));
        };
    }

    private StepResult check(StepContext ctx) {
        if (healthService.monitoringEnabled()) {
            try {
                healthService.sample(
                        OffsetDateTime.ofInstant(Instant.ofEpochMilli(ctx.clock().currentTimeMillis()), ZoneOffset.UTC),
                        true);
            } catch (Exception ex) {
                log.warn("Server runtime health monitor check failed", ex);
            }
        }
        ctx.startTimeout(healthService.effectiveCheckIntervalMs());
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
