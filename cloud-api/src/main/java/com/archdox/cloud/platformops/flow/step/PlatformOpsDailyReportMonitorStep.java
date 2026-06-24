package com.archdox.cloud.platformops.flow.step;

import com.archdox.cloud.platformops.application.PlatformOpsDailyReportMonitorService;
import com.archdox.cloud.platformops.event.PlatformOpsDailyReportRequested;
import com.archdox.cloud.platformops.flow.PlatformOpsDailyReportFlowFactory;
import com.archdox.cloud.platformops.flow.PlatformOpsWorker;
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
    private final PlatformOpsDailyReportFlowFactory dailyReportFlowFactory;
    private final PlatformOpsWorker platformOpsWorker;

    public PlatformOpsDailyReportMonitorStep(
            PlatformOpsDailyReportMonitorService monitorService,
            PlatformOpsDailyReportFlowFactory dailyReportFlowFactory,
            PlatformOpsWorker platformOpsWorker
    ) {
        this.monitorService = monitorService;
        this.dailyReportFlowFactory = dailyReportFlowFactory;
        this.platformOpsWorker = platformOpsWorker;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        return switch (ctx.stepNo()) {
            case CHECK -> check(ctx);
            case WAIT -> waitUntilNextCheck(ctx);
            default -> StepResult.fail(new IllegalStateException(
                    "Unknown platform ops daily report monitor stepNo: " + ctx.stepNo()));
        };
    }

    private StepResult check(StepContext ctx) {
        try {
            var decision = monitorService.checkAndRequestIfDue(
                    OffsetDateTime.ofInstant(Instant.ofEpochMilli(ctx.clock().currentTimeMillis()), ZoneOffset.UTC));
            if ("REQUESTED".equals(decision.status())) {
                platformOpsWorker.submit(dailyReportFlowFactory.create(new PlatformOpsDailyReportRequested(
                        decision.opsRunId(),
                        decision.dueAt(),
                        null)));
                log.info("Platform ops daily report requested run {}", decision.opsRunId());
            }
        } catch (Exception ex) {
            log.warn("Platform ops daily report monitor check failed", ex);
        }
        ctx.startTimeout(monitorService.checkIntervalMs());
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
