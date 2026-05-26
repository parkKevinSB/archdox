package com.archdox.cloud.agent.flow.step;

import com.archdox.cloud.agent.application.ArchDoxAgentConnectionHealthProperties;
import com.archdox.cloud.agent.application.ArchDoxAgentConnectionHealthService;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentConnectionHealthMonitorStep extends Step {
    private static final Logger log = LoggerFactory.getLogger(AgentConnectionHealthMonitorStep.class);
    private static final int CHECK = 0;
    private static final int WAIT = 100;

    private final ArchDoxAgentConnectionHealthService healthService;
    private final ArchDoxAgentConnectionHealthProperties properties;

    public AgentConnectionHealthMonitorStep(
            ArchDoxAgentConnectionHealthService healthService,
            ArchDoxAgentConnectionHealthProperties properties
    ) {
        this.healthService = healthService;
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
                    "Unknown agent connection health stepNo: " + ctx.stepNo()));
        };
    }

    private StepResult check(StepContext ctx) {
        try {
            healthService.disconnectHeartbeatTimedOutSessions();
        } catch (Exception ex) {
            log.warn("ArchDox Agent connection health check failed", ex);
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
