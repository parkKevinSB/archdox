package com.archdox.cloud.platformops.flow.step;

import com.archdox.cloud.platformops.application.PlatformOpsDailyReportMonitorService;
import com.archdox.cloud.platformops.application.PlatformOpsDailyReportProperties;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlatformOpsDailyReportMonitorStep extends Step {
    private static final Logger log = LoggerFactory.getLogger(PlatformOpsDailyReportMonitorStep.class);
    private static final int CHECK = 0;
    private static final int WAIT = 100;

    private final PlatformOpsDailyReportMonitorService monitorService;
    private final PlatformOpsDailyReportProperties properties;

    public PlatformOpsDailyReportMonitorStep(
            PlatformOpsDailyReportMonitorService monitorService,
            PlatformOpsDailyReportProperties properties
    ) {
        this.monitorService = monitorService;
        this.properties = properties;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        if (!properties.isEnabled()) {
            return StepResult.done();
        }
        return switch (ctx.stepNo()) {
            case CHECK -> check(ctx);
            case WAIT -> waitUntilNextCheck(ctx);
            default -> StepResult.fail(new IllegalStateException(
                    "Unknown platform ops daily report monitor stepNo: " + ctx.stepNo()));
        };
    }

    private StepResult check(StepContext ctx) {
        try {
            var decision = monitorService.checkAndGenerateIfDue(
                    OffsetDateTime.ofInstant(Instant.ofEpochMilli(ctx.clock().currentTimeMillis()), ZoneOffset.UTC));
            if ("GENERATED".equals(decision.status())) {
                log.info("Platform ops daily report generated run {} at {}", decision.opsRunId(), decision.reportPath());
            }
        } catch (Exception ex) {
            log.warn("Platform ops daily report monitor check failed", ex);
        }
        ctx.startTimeout(properties.safeCheckIntervalMs());
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
