package com.archdox.worker.application;

import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchDoxWorkerActionRegistryTest {
    @Test
    void resolves_registered_executor() {
        var executor = new StubExecutor(ArchDoxWorkerActionType.RUN_DOCUMENT_QA);
        var registry = new ArchDoxWorkerActionRegistry(List.of(executor));

        assertThat(registry.resolve(ArchDoxWorkerActionType.RUN_DOCUMENT_QA)).contains(executor);
        assertThat(registry.registeredActionTypes()).containsExactly(ArchDoxWorkerActionType.RUN_DOCUMENT_QA);
    }

    @Test
    void rejects_duplicate_executor_for_same_action() {
        var first = new StubExecutor(ArchDoxWorkerActionType.RUN_DOCUMENT_QA);
        var second = new StubExecutor(ArchDoxWorkerActionType.RUN_DOCUMENT_QA);

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
