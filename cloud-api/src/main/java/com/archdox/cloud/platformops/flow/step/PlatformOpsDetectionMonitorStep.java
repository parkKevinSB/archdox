package com.archdox.cloud.platformops.flow.step;

import com.archdox.cloud.platformops.application.PlatformOpsDetectionMonitorService;
import com.archdox.cloud.platformops.application.PlatformOpsDetectionProperties;
import com.archdox.cloud.platformops.event.PlatformOpsDetectionRequested;
import com.archdox.cloud.platformops.flow.PlatformOpsDetectionFlowFactory;
import com.archdox.cloud.platformops.flow.PlatformOpsWorker;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlatformOpsDetectionMonitorStep extends Step {
    private static final Logger log = LoggerFactory.getLogger(PlatformOpsDetectionMonitorStep.class);
    private static final int CHECK = 0;
    private static final int WAIT = 100;

    private final PlatformOpsDetectionMonitorService monitorService;
    private final PlatformOpsDetectionProperties properties;
    private final PlatformOpsDetectionFlowFactory detectionFlowFactory;
    private final PlatformOpsWorker platformOpsWorker;

    public PlatformOpsDetectionMonitorStep(
            PlatformOpsDetectionMonitorService monitorService,
            PlatformOpsDetectionProperties properties,
            PlatformOpsDetectionFlowFactory detectionFlowFactory,
            PlatformOpsWorker platformOpsWorker
    ) {
        this.monitorService = monitorService;
        this.properties = properties;
        this.detectionFlowFactory = detectionFlowFactory;
        this.platformOpsWorker = platformOpsWorker;
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
                    "Unknown platform ops detection monitor stepNo: " + ctx.stepNo()));
        };
    }

    private StepResult check(StepContext ctx) {
        try {
            var decision = monitorService.checkAndRequestIfDue(
                    OffsetDateTime.ofInstant(Instant.ofEpochMilli(ctx.clock().currentTimeMillis()), ZoneOffset.UTC));
            if ("REQUESTED".equals(decision.status())) {
                platformOpsWorker.submit(detectionFlowFactory.create(new PlatformOpsDetectionRequested(
                        decision.opsRunId(),
                        null)));
                log.info("Platform ops detection requested run {}", decision.opsRunId());
            }
        } catch (Exception ex) {
            log.warn("Platform ops detection monitor check failed", ex);
        }
        ctx.startTimeout(properties.safeWorkerIntervalMs());
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
