package com.archdox.cloud.engine.usage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.archdox.cloud.engine.auth.application.EngineApiPrincipal;
import com.archdox.cloud.engine.usage.domain.EngineApiUsageEvent;
import com.archdox.cloud.engine.usage.infra.EngineApiUsageEventRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EngineApiUsageServiceTest {
    private final EngineApiUsageEventRepository repository = mock(EngineApiUsageEventRepository.class);
    private final EngineApiUsageService service = new EngineApiUsageService(repository);

    @Test
    void recordsReviewSessionUsageForExternalEngineApiKey() {
        var principal = new EngineApiPrincipal(
                11L,
                "key_test",
                22L,
                33L,
                List.of("ENGINE_REVIEW_SESSION"),
                1000);

        service.recordReviewSessionUsage(
                principal,
                "RUN_VALIDATION",
                "rvw_sess_001",
                Map.of("findingCount", 2));

        var captor = ArgumentCaptor.forClass(EngineApiUsageEvent.class);
        verify(repository).save(captor.capture());
        var event = captor.getValue();
        assertThat(event.apiKeyId()).isEqualTo(11L);
        assertThat(event.keyId()).isEqualTo("key_test");
        assertThat(event.ownerUserId()).isEqualTo(22L);
        assertThat(event.officeId()).isEqualTo(33L);
        assertThat(event.capability()).isEqualTo(EngineApiUsageService.CAPABILITY_REVIEW_SESSION);
        assertThat(event.operation()).isEqualTo("RUN_VALIDATION");
        assertThat(event.reviewSessionId()).isEqualTo("rvw_sess_001");
        assertThat(event.status()).isEqualTo(EngineApiUsageService.STATUS_SUCCEEDED);
        assertThat(event.requestUnits()).isEqualTo(1);
        assertThat(event.metadataJson()).containsEntry("findingCount", 2);
    }
}
