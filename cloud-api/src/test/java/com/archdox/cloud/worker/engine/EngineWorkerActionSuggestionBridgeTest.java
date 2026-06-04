package com.archdox.cloud.worker.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.archdox.cloud.engine.application.ArchDoxEngineResultStatus;
import com.archdox.cloud.engine.application.EngineValidationResult;
import com.archdox.worker.application.ArchDoxWorkerActionExecutor;
import com.archdox.worker.application.ArchDoxWorkerActionRegistry;
import com.archdox.worker.application.ArchDoxWorkerExecutionContext;
import com.archdox.worker.domain.ArchDoxWorkerActionOrigin;
import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class EngineWorkerActionSuggestionBridgeTest {
    @Test
    void convertsEngineSuggestionsToWorkerActionCandidatesWithoutExecutingThem() {
        var registry = new ArchDoxWorkerActionRegistry(List.of(
                executor(ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW)));
        var bridge = new EngineWorkerActionSuggestionBridge(provider(registry));
        var result = result(List.of("RUN_PREFLIGHT_REVIEW", "CREATE_REPORT", "NOT_A_WORKER_ACTION"));

        var candidates = bridge.candidates(result, Map.of("reportId", 3L));

        assertThat(candidates).hasSize(3);
        assertThat(candidates.get(0).known()).isTrue();
        assertThat(candidates.get(0).enabled()).isTrue();
        assertThat(candidates.get(0).executorRegistered()).isTrue();
        assertThat(candidates.get(0).action().actionType()).isEqualTo(ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW);
        assertThat(candidates.get(0).action().origin()).isEqualTo(ArchDoxWorkerActionOrigin.PLANNER);
        assertThat(candidates.get(0).action().payload())
                .containsEntry("engineRunId", "eng_test")
                .containsEntry("reportId", 3L);

        assertThat(candidates.get(1).known()).isTrue();
        assertThat(candidates.get(1).enabled()).isTrue();
        assertThat(candidates.get(1).executorRegistered()).isFalse();
        assertThat(candidates.get(1).action().actionType()).isEqualTo(ArchDoxWorkerActionType.CREATE_REPORT);

        assertThat(candidates.get(2).known()).isFalse();
        assertThat(candidates.get(2).action()).isNull();
    }

    @Test
    void candidateMetadataIsSafeForOperationEvents() {
        var registry = new ArchDoxWorkerActionRegistry(List.of());
        var bridge = new EngineWorkerActionSuggestionBridge(provider(registry));

        var metadata = bridge.candidateMetadata(result(List.of("CREATE_REPORT")), Map.of());

        assertThat(metadata).singleElement()
                .satisfies(candidate -> assertThat(candidate)
                        .containsEntry("suggestedAction", "CREATE_REPORT")
                        .containsEntry("known", true)
                        .containsEntry("enabled", true)
                        .containsEntry("executorRegistered", false));
    }

    private EngineValidationResult result(List<String> suggestedActions) {
        return new EngineValidationResult(
                "eng_test",
                ArchDoxEngineResultStatus.WARN,
                false,
                "summary",
                List.of(),
                List.of("RUN_VALIDATION_AGAIN"),
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
                throw new AssertionError("Bridge must not execute Worker actions");
            }
        };
    }

    private ObjectProvider<ArchDoxWorkerActionRegistry> provider(ArchDoxWorkerActionRegistry registry) {
        return new ObjectProvider<>() {
            @Override
            public ArchDoxWorkerActionRegistry getObject(Object... args) {
                return registry;
            }

            @Override
            public ArchDoxWorkerActionRegistry getObject() {
                return registry;
            }

            @Override
            public ArchDoxWorkerActionRegistry getIfAvailable() {
                return registry;
            }

            @Override
            public ArchDoxWorkerActionRegistry getIfUnique() {
                return registry;
            }

            @Override
            public Iterator<ArchDoxWorkerActionRegistry> iterator() {
                return List.of(registry).iterator();
            }
        };
    }
}
