package com.archdox.agent.launcher;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentUpdatePlannerTest {
    private final AgentUpdatePlanner planner = new AgentUpdatePlanner();

    @Test
    void compatibleRuntimeIsOk() {
        var decision = planner.decide(
                manifest("2026-06-25", "0.0.1-dev", "0.0.1-dev"),
                "0.0.1-dev",
                "2026-06-25",
                "embedded");

        assertThat(decision.status()).isEqualTo("OK");
        assertThat(decision.runtimeUpdateRequired()).isFalse();
    }

    @Test
    void protocolBelowMinimumRequiresRuntimeUpdate() {
        var decision = planner.decide(
                manifest("2026-06-25", "0.0.1-dev", "0.0.1-dev"),
                "0.0.1-dev",
                "2026-06-24",
                "embedded");

        assertThat(decision.status()).isEqualTo("UPDATE_REQUIRED");
        assertThat(decision.runtimeUpdateRequired()).isTrue();
        assertThat(decision.reason()).contains("protocolVersion");
    }

    @Test
    void runtimeBelowMinimumRequiresUpdate() {
        var decision = planner.decide(
                manifest("2026-06-25", "1.2.0", "1.2.0"),
                "1.1.9",
                "2026-06-25",
                "embedded");

        assertThat(decision.status()).isEqualTo("UPDATE_REQUIRED");
        assertThat(decision.runtimeUpdateRequired()).isTrue();
        assertThat(decision.reason()).contains("runtime version");
    }

    @Test
    void runtimeBelowRecommendedIsOnlyRecommendedWhenMinimumIsMet() {
        var decision = planner.decide(
                manifest("2026-06-25", "1.2.0", "1.3.0"),
                "1.2.5",
                "2026-06-25",
                "embedded");

        assertThat(decision.status()).isEqualTo("UPDATE_RECOMMENDED");
        assertThat(decision.runtimeUpdateRequired()).isFalse();
        assertThat(decision.runtimeUpdateRecommended()).isTrue();
    }

    private AgentRuntimeManifest manifest(
            String minimumProtocolVersion,
            String minimumAgentVersion,
            String recommendedAgentVersion
    ) {
        return new AgentRuntimeManifest(
                "stable",
                "windows-x64",
                "2026-06-25",
                minimumProtocolVersion,
                minimumAgentVersion,
                recommendedAgentVersion,
                recommendedAgentVersion,
                "embedded",
                "embedded",
                false,
                null,
                null,
                null,
                null,
                "2026-06-25T00:00:00Z");
    }
}
