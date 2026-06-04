package com.archdox.cloud.engine.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArchDoxEngineServiceTest {
    private final ArchDoxEngineService service = new ArchDoxEngineService();

    @Test
    void preparesBoundaryResponseWithoutExecutingBusinessWorkflow() {
        var request = new ArchDoxEngineRequest(
                ArchDoxEngineRunMode.SAAS_CONTEXT,
                Set.of(ArchDoxEngineCapability.PREFLIGHT_REVIEW, ArchDoxEngineCapability.LEGAL_RISK_REVIEW),
                LocalDate.parse("2026-06-04"),
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                3L,
                1L,
                10L,
                20L,
                30L,
                2,
                null);

        var response = service.prepare(request);

        assertThat(response.engineRunId()).startsWith("eng_");
        assertThat(response.status()).isEqualTo(ArchDoxEngineResultStatus.PENDING);
        assertThat(response.generationAllowed()).isFalse();
        assertThat(response.findings()).isEmpty();
        assertThat(response.nextActions()).contains(
                "SUBMIT_CONTEXT",
                "NORMALIZE_CONTEXT",
                "RUN_ENGINE_RECIPE_VALIDATION");
        assertThat(response.metadata()).containsEntry("boundary", "ARCHDOX_ENGINE_BOUNDARY");
        assertThat(response.metadata()).containsEntry("governanceBoundary", "ARCHDOX_WORKER_SERVICE");
        assertThat(response.metadata()).containsEntry("workerGovernanceExecuted", false);
        assertThat(response.metadata()).containsKey("requestedCapabilities");
    }
}
