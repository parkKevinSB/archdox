package com.archdox.cloud.aiharness.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class AiWorkerEvaluationReadServiceTest {
    private final PlatformAdminService platformAdminService = mock(PlatformAdminService.class);
    private final AiWorkerEvaluationReadService service = new AiWorkerEvaluationReadService(platformAdminService);

    @Test
    void summaryExposesGroupedControlEvaluationScorecard() {
        var principal = new UserPrincipal(7L, "platform@test.co.kr");

        var summary = service.summary(principal);

        assertThat(summary.evaluationMode()).isEqualTo("STATIC_BASELINE");
        assertThat(summary.totalCases()).isEqualTo(51);
        assertThat(summary.automatedCases()).isEqualTo(51);
        assertThat(summary.passedCases()).isEqualTo(51);
        assertThat(summary.warningCases()).isZero();
        assertThat(summary.failedCases()).isZero();
        assertThat(summary.passRatePercent()).isEqualTo(100);
        assertThat(summary.groups()).hasSize(5);
        assertThat(summary.signals()).hasSize(11);
        assertThat(summary.signals()).anySatisfy(signal -> {
            assertThat(signal.signalKey()).isEqualTo("REAL_MODEL_EVALUATION");
            assertThat(signal.status()).isEqualTo("WARN");
        });
        assertThat(summary.groups())
                .extracting(group -> group.groupKey())
                .containsExactly(
                        "AI_HARNESS_BASELINE",
                        "WORKER_CONTROL_BASELINE",
                        "MCP_ENGINE_BOUNDARY",
                        "LEGAL_DIGEST_PIPELINE",
                        "WORKER_POLICY_GOVERNANCE");
        assertThat(summary.groups())
                .allSatisfy(group -> assertThat(group.passRatePercent()).isEqualTo(100));

        var ids = new HashSet<String>();
        summary.groups().forEach(group -> group.cases().forEach(testCase -> {
            assertThat(testCase.status()).isEqualTo("PASS");
            assertThat(testCase.automated()).isTrue();
            assertThat(testCase.evidence()).isNotBlank();
            assertThat(ids.add(testCase.caseId())).as("duplicate case id %s", testCase.caseId()).isTrue();
        }));
        verify(platformAdminService).requirePlatformAdmin(principal);
    }
}
