package com.archdox.cloud.aiharness.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.aiharness.domain.AiWorkerEvaluationRun;
import com.archdox.cloud.aiharness.infra.AiWorkerEvaluationRunRepository;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiWorkerEvaluationRunServiceTest {
    private final AiWorkerEvaluationRunRepository repository = mock(AiWorkerEvaluationRunRepository.class);
    private final PlatformAdminService platformAdminService = mock(PlatformAdminService.class);
    private final AiWorkerEvaluationReadService readService = new AiWorkerEvaluationReadService(platformAdminService);
    private final AiWorkerEvaluationRunService service = new AiWorkerEvaluationRunService(
            repository,
            readService,
            platformAdminService,
            JsonMapper.builder().addModule(new JavaTimeModule()).build());

    @Test
    void createSnapshotStoresCurrentEvaluationSummaryAsRun() {
        var principal = new UserPrincipal(7L, "platform@test.co.kr");
        when(repository.save(any(AiWorkerEvaluationRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createSnapshot(principal);

        var captor = ArgumentCaptor.forClass(AiWorkerEvaluationRun.class);
        verify(repository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.runKey()).startsWith("aiw_eval_");
        assertThat(saved.triggerType()).isEqualTo("PLATFORM_ADMIN_SNAPSHOT");
        assertThat(saved.status()).isEqualTo("WARN");
        assertThat(saved.evaluationMode()).isEqualTo("STATIC_BASELINE");
        assertThat(saved.totalCases()).isEqualTo(47);
        assertThat(saved.automatedCases()).isEqualTo(47);
        assertThat(saved.passedCases()).isEqualTo(47);
        assertThat(saved.warningCases()).isZero();
        assertThat(saved.failedCases()).isZero();
        assertThat(saved.groupCount()).isEqualTo(5);
        assertThat(saved.signalCount()).isEqualTo(11);
        assertThat(saved.warningSignalCount()).isEqualTo(1);
        assertThat(saved.failedSignalCount()).isZero();
        assertThat(saved.summaryJson()).containsEntry("totalCases", 47);
        assertThat(response.status()).isEqualTo("WARN");
        assertThat(response.summary().totalCases()).isEqualTo(47);
        assertThat(response.summary().signals()).anySatisfy(signal -> {
            assertThat(signal.signalKey()).isEqualTo("REAL_MODEL_EVALUATION");
            assertThat(signal.status()).isEqualTo("WARN");
        });
        verify(platformAdminService).requirePlatformAdmin(principal);
    }
}
