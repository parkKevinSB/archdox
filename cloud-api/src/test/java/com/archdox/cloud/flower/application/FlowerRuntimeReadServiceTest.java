package com.archdox.cloud.flower.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.infra.OperationEventRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import io.github.parkkevinsb.flower.core.worker.Worker;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class FlowerRuntimeReadServiceTest {
    @Test
    void dumpsEngineWorkersFlowsStepsExecutionContextAndExecutorsForPlatformAdmin() throws Exception {
        var platformAdminService = mock(PlatformAdminService.class);
        var operationEventRepository = mock(OperationEventRepository.class);
        var operationEventService = mock(OperationEventService.class);
        when(operationEventRepository.findByEventTypeInOrderByCreatedAtDescIdDesc(any(), any()))
                .thenReturn(List.of());
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

        var releaseExecutor = new CountDownLatch(1);
        var executorStarted = new CountDownLatch(1);
        var executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1));
        try {
            executor.execute(() -> {
                executorStarted.countDown();
                try {
                    releaseExecutor.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(executorStarted.await(2, TimeUnit.SECONDS)).isTrue();
            executor.execute(() -> {
            });

            var principal = new UserPrincipal(1L, "vvzerg@test.co.kr");
            var dump = new FlowerRuntimeReadService(
                    platformAdminService,
                    engine,
                    Map.of("documentGenerationExecutor", executor),
                    operationEventRepository,
                    operationEventService)
                    .dump(principal);

            assertThat(dump.workerCount()).isEqualTo(1);
            assertThat(dump.activeFlowCount()).isEqualTo(1);
            assertThat(dump.executorCount()).isEqualTo(1);
            assertThat(dump.saturatedExecutorCount()).isEqualTo(1);
            assertThat(dump.queuedTaskCount()).isEqualTo(1);
            assertThat(dump.overloadEvents()).isEmpty();
            assertThat(dump.executors()).singleElement()
                    .satisfies(executorDump -> {
                        assertThat(executorDump.beanName()).isEqualTo("documentGenerationExecutor");
                        assertThat(executorDump.state()).isEqualTo("SATURATED");
                        assertThat(executorDump.activeCount()).isEqualTo(1);
                        assertThat(executorDump.queueSize()).isEqualTo(1);
                        assertThat(executorDump.remainingQueueCapacity()).isZero();
                    });
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
        } finally {
            releaseExecutor.countDown();
            executor.shutdownNow();
        }
    }

    private static final class WaitingStep extends Step {
        @Override
        protected StepResult onTick(StepContext ctx) {
            return StepResult.stay();
        }
    }
}
