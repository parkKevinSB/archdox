package com.archdox.worker.application;

import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.domain.ArchDoxWorkerActionRiskLevel;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchDoxWorkerActionRegistryTest {
    @Test
    void resolves_registered_executor() {
        var executor = new StubExecutor(ArchDoxWorkerActionType.CREATE_SITE);
        var registry = new ArchDoxWorkerActionRegistry(List.of(executor));

        assertThat(registry.resolve(ArchDoxWorkerActionType.CREATE_SITE)).contains(executor);
        assertThat(registry.registeredActionTypes()).containsExactly(ArchDoxWorkerActionType.CREATE_SITE);
    }

    @Test
    void exposes_action_definition_even_when_executor_is_not_registered() {
        var registry = new ArchDoxWorkerActionRegistry(List.of());

        var definition = registry.definition(ArchDoxWorkerActionType.REQUEST_DOCUMENT_GENERATION);

        assertThat(definition).isPresent();
        assertThat(definition.get().enabled()).isTrue();
        assertThat(definition.get().riskLevel()).isEqualTo(ArchDoxWorkerActionRiskLevel.HIGH);
        assertThat(definition.get().requiredContextFields()).contains("userId", "officeId", "projectId");
        assertThat(registry.resolve(ArchDoxWorkerActionType.REQUEST_DOCUMENT_GENERATION)).isEmpty();
    }

    @Test
    void exposes_only_actions_with_runtime_contracts() {
        var registry = new ArchDoxWorkerActionRegistry(List.of());

        assertThat(registry.actionDefinitionMetadata())
                .extracting(metadata -> metadata.get("actionType"))
                .containsExactlyInAnyOrder(
                        "WORKER_CHAT_ADVANCE",
                        "CREATE_SITE",
                        "CREATE_REPORT",
                        "UPDATE_REPORT_STEP",
                        "SUBMIT_REPORT",
                        "RUN_PREFLIGHT_REVIEW",
                        "REQUEST_DOCUMENT_GENERATION");
    }

    @Test
    void rejects_duplicate_executor_for_same_action() {
        var first = new StubExecutor(ArchDoxWorkerActionType.CREATE_REPORT);
        var second = new StubExecutor(ArchDoxWorkerActionType.CREATE_REPORT);

        assertThatThrownBy(() -> new ArchDoxWorkerActionRegistry(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate ArchDox Worker action executor");
    }

    private record StubExecutor(ArchDoxWorkerActionType actionType) implements ArchDoxWorkerActionExecutor {
        @Override
        public ArchDoxWorkerActionResult execute(ArchDoxWorkerExecutionContext context) {
            return ArchDoxWorkerActionResult.succeeded(null);
        }
    }
}
