package com.archdox.cloud.worker.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.archdox.cloud.engine.application.ArchDoxEngineResultStatus;
import com.archdox.cloud.engine.application.EngineValidationResult;
import com.archdox.cloud.worker.ArchDoxWorkerServiceWorker;
import com.archdox.worker.application.ArchDoxWorkerActionExecutor;
import com.archdox.worker.application.ArchDoxWorkerActionRegistry;
import com.archdox.worker.application.ArchDoxWorkerExecutionContext;
import com.archdox.worker.application.ArchDoxWorkerPolicyGate;
import com.archdox.worker.application.ArchDoxWorkerTraceSink;
import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.domain.ArchDoxWorkerRequestContext;
import com.archdox.worker.domain.ArchDoxWorkerRequestSource;
import com.archdox.worker.flow.ArchDoxWorkerExecutionFlowFactory;
import io.github.parkkevinsb.flower.core.flow.Flow;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class EngineWorkerActionSubmissionServiceTest {
    @Test
    void submitsRunnableCandidateToWorkerFlow() {
        var registry = new ArchDoxWorkerActionRegistry(List.of(executor(ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW)));
        var worker = new CapturingWorker();
        var service = service(registry, worker);

        var result = service.submitAfterCommit(
                result(List.of("RUN_PREFLIGHT_REVIEW")),
                request(
                        Map.of("sessionId", 10L, "assistantMessageId", 20L, "reportId", 3L),
                        Set.of()));

        assertThat(result.submitted()).singleElement()
                .satisfies(submitted -> assertThat(submitted)
                        .containsEntry("actionType", "RUN_PREFLIGHT_REVIEW"));
        assertThat(result.skipped()).isEmpty();
        assertThat(worker.submitted).hasSize(1);
    }

    @Test
    void skipsWorkerChatScopedCandidateWithoutChatPayload() {
        var registry = new ArchDoxWorkerActionRegistry(List.of(executor(ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW)));
        var worker = new CapturingWorker();
        var service = service(registry, worker);

        var result = service.submitAfterCommit(
                result(List.of("RUN_PREFLIGHT_REVIEW")),
                request(Map.of("reportId", 3L), Set.of()));

        assertThat(result.submitted()).isEmpty();
        assertThat(result.skipped()).singleElement()
                .satisfies(skipped -> assertThat(skipped.get("reason").toString())
                        .contains("Worker Chat scoped"));
        assertThat(worker.submitted).isEmpty();
    }

    @Test
    void skipsExcludedCandidateToPreventRecursiveEngineFlow() {
        var registry = new ArchDoxWorkerActionRegistry(List.of(executor(ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW)));
        var worker = new CapturingWorker();
        var service = service(registry, worker);

        var result = service.submitAfterCommit(
                result(List.of("RUN_PREFLIGHT_REVIEW")),
                request(
                        Map.of("sessionId", 10L, "assistantMessageId", 20L, "reportId", 3L),
                        Set.of(ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW)));

        assertThat(result.submitted()).isEmpty();
        assertThat(result.skipped()).singleElement()
                .satisfies(skipped -> assertThat(skipped.get("reason").toString())
                        .contains("excluded"));
        assertThat(worker.submitted).isEmpty();
    }

    private EngineWorkerActionSubmissionService service(
            ArchDoxWorkerActionRegistry registry,
            CapturingWorker worker
    ) {
        var bridge = new EngineWorkerActionSuggestionBridge(provider(registry));
        var factory = new ArchDoxWorkerExecutionFlowFactory(
                registry,
                ArchDoxWorkerPolicyGate.allowAll(),
                ArchDoxWorkerTraceSink.noop());
        return new EngineWorkerActionSubmissionService(
                bridge,
                provider(factory),
                provider(worker));
    }

    private EngineWorkerActionSubmissionRequest request(
            Map<String, Object> payload,
            Set<ArchDoxWorkerActionType> excludedActionTypes
    ) {
        return new EngineWorkerActionSubmissionRequest(
                null,
                ArchDoxWorkerRequestSource.UI,
                "test",
                new ArchDoxWorkerRequestContext(1L, 2L, 3L, 4L, 5L, null, "ko-KR"),
                payload,
                excludedActionTypes);
    }

    private EngineValidationResult result(List<String> suggestedActions) {
        return new EngineValidationResult(
                "eng_test",
                ArchDoxEngineResultStatus.PASS,
                true,
                "summary",
                List.of(),
                List.of(),
                List.of("RESULT_READY"),
                "NOT_APPLICABLE_ENGINE_RECIPE_ONLY",
                List.of("CATALOG_BINDING_REVIEW"),
                "ENGINE_RECIPE_VALIDATION",
                Map.of("suggestedWorkerActions", suggestedActions));
    }

    private ArchDoxWorkerActionExecutor executor(ArchDoxWorkerActionType actionType) {
        return new ArchDoxWorkerActionExecutor() {
            @Override
            public ArchDoxWorkerActionType actionType() {
                return actionType;
            }

            @Override
            public ArchDoxWorkerActionResult execute(ArchDoxWorkerExecutionContext context) {
                return ArchDoxWorkerActionResult.succeeded(Map.of());
            }
        };
    }

    private <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public Iterator<T> iterator() {
                return List.of(value).iterator();
            }
        };
    }

    private static final class CapturingWorker extends ArchDoxWorkerServiceWorker {
        private final List<Flow> submitted = new ArrayList<>();

        private CapturingWorker() {
            super(null);
        }

        @Override
        public void submit(Flow flow) {
            submitted.add(flow);
        }
    }
}
