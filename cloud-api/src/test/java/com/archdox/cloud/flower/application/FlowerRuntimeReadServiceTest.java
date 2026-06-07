package com.archdox.cloud.flower.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import io.github.parkkevinsb.flower.core.worker.Worker;
import org.junit.jupiter.api.Test;

class FlowerRuntimeReadServiceTest {
    @Test
    void dumpsEngineWorkersFlowsStepsAndExecutionContextForPlatformAdmin() {
        var platformAdminService = mock(PlatformAdminService.class);
        var worker = Worker.builder("archdox-worker-test").build();
        var engine = Engine.builder()
                .worker(worker)
                .build();
        engine.attach();
        var flow = Flow.builder("archdox-worker-execution", "request-1:RUN_PREFLIGHT_REVIEW")
                .step("wait", new WaitingStep())
                .executionContext(ExecutionContext.builder()
                        .tenantId("20")
                        .userId("10")
                        .runId("request-1")
                        .correlationId("corr-1")
                        .build())
                .build();
        worker.submit(flow);
        worker.tickOnce();

        var principal = new UserPrincipal(1L, "vvzerg@test.co.kr");
        var dump = new FlowerRuntimeReadService(platformAdminService, engine).dump(principal);

        assertThat(dump.workerCount()).isEqualTo(1);
        assertThat(dump.activeFlowCount()).isEqualTo(1);
        assertThat(dump.workers()).singleElement()
                .satisfies(workerDump -> {
                    assertThat(workerDump.name()).isEqualTo("archdox-worker-test");
                    assertThat(workerDump.flows()).singleElement()
                            .satisfies(flowDump -> {
                                assertThat(flowDump.flowType()).isEqualTo("archdox-worker-execution");
                                assertThat(flowDump.currentStepId()).isEqualTo("wait");
                                assertThat(flowDump.executionContext().tenantId()).isEqualTo("20");
                                assertThat(flowDump.executionContext().userId()).isEqualTo("10");
                                assertThat(flowDump.steps()).singleElement()
                                        .satisfies(step -> {
                                            assertThat(step.stepId()).isEqualTo("wait");
                                            assertThat(step.current()).isTrue();
                                        });
                            });
                });
        verify(platformAdminService).requirePlatformAdmin(principal);
    }

    private static final class WaitingStep extends Step {
        @Override
        protected StepResult onTick(StepContext ctx) {
            return StepResult.stay();
        }
    }
}
