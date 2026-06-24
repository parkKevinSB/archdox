package com.archdox.cloud.platformops.flow.step;

import com.archdox.cloud.platformops.application.PlatformOpsRetentionService;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlatformOpsRetentionMonitorStep extends Step {
    private static final Logger log = LoggerFactory.getLogger(PlatformOpsRetentionMonitorStep.class);
    private static final int CHECK = 0;
    private static final int WAIT = 100;

    private final PlatformOpsRetentionService retentionService;

    public PlatformOpsRetentionMonitorStep(PlatformOpsRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        return switch (ctx.stepNo()) {
            case CHECK -> check(ctx);
            case WAIT -> waitUntilNextCheck(ctx);
            default -> StepResult.fail(new IllegalStateException(
                    "Unknown platform ops retention monitor stepNo: " + ctx.stepNo()));
        };
    }

    private StepResult check(StepContext ctx) {
        try {
            var result = retentionService.purgeExpired(
                    OffsetDateTime.ofInstant(Instant.ofEpochMilli(ctx.clock().currentTimeMillis()), ZoneOffset.UTC));
            if (result.totalDeleted() > 0) {
                log.info(
                        "Platform ops retention purged {} daily reports, {} findings, {} incidents, {} runs, and {} log projection events before {}",
                        result.deletedDailyReports(),
                        result.deletedFindings(),
                        result.deletedIncidents(),
                        result.deletedRuns(),
                        result.deletedLogProjectionEvents(),
                        result.cutoff());
            }
        } catch (Exception ex) {
            log.warn("Platform ops retention monitor check failed", ex);
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
