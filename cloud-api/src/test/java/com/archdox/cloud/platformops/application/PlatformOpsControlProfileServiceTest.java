package com.archdox.cloud.platformops.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.cloud.platformops.domain.PlatformOpsControlProfile;
import com.archdox.cloud.platformops.domain.PlatformOpsControlProfileScope;
import com.archdox.cloud.platformops.domain.PlatformOpsControlSignalKind;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.dto.CreatePlatformOpsControlProfileRequest;
import com.archdox.cloud.platformops.infra.PlatformOpsControlProfileRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PlatformOpsControlProfileServiceTest {
    private final PlatformAdminService platformAdminService = mock(PlatformAdminService.class);
    private final PlatformOpsControlProfileRepository repository = mock(PlatformOpsControlProfileRepository.class);
    private final PlatformOpsControlProfileService service = new PlatformOpsControlProfileService(
            platformAdminService,
            repository);
    private final UserPrincipal principal = new UserPrincipal(7L, "platform@test.co.kr");

    @Test
    void createsGlobalILikeControlProfile() {
        when(repository.findSignal(
                eq(PlatformOpsControlSignalKind.I_LIKE),
                eq(PlatformOpsControlProfileScope.GLOBAL),
                eq(null),
                any())).thenReturn(Optional.empty());
        when(repository.save(any(PlatformOpsControlProfile.class))).thenAnswer(invocation -> {
            var profile = invocation.getArgument(0, PlatformOpsControlProfile.class);
            ReflectionTestUtils.setField(profile, "id", 10L);
            return profile;
        });

        var response = service.create(principal, new CreatePlatformOpsControlProfileRequest(
                "I_LIKE",
                "GLOBAL",
                null,
                "Document generation failures repeat for one office.",
                "WARN",
                new BigDecimal("2.5"),
                33L,
                "Operator approved this as a recurring signal."));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.signalKind()).isEqualTo("I_LIKE");
        assertThat(response.scopeType()).isEqualTo("GLOBAL");
        assertThat(response.modelId()).isNull();
        assertThat(response.iWeight()).isEqualByComparingTo("2.5");
        assertThat(response.hitCount()).isEqualTo(1);
        verify(platformAdminService).requirePlatformAdmin(principal);
    }

    @Test
    void reObservesSameModelSignalInsteadOfCreatingDuplicate() {
        var existing = new PlatformOpsControlProfile(
                PlatformOpsControlSignalKind.I_LIKE,
                PlatformOpsControlProfileScope.MODEL,
                "openai-main:gpt-4.1-mini",
                "same-key",
                "Legal review validation retry repeats.",
                PlatformOpsFindingSeverity.WARN,
                BigDecimal.ONE,
                20L,
                "first note",
                7L,
                OffsetDateTime.parse("2026-06-25T00:00:00Z"));
        ReflectionTestUtils.setField(existing, "id", 21L);

        when(repository.findSignal(
                eq(PlatformOpsControlSignalKind.I_LIKE),
                eq(PlatformOpsControlProfileScope.MODEL),
                eq("openai-main:gpt-4.1-mini"),
                any())).thenReturn(Optional.of(existing));
        when(repository.save(any(PlatformOpsControlProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(principal, new CreatePlatformOpsControlProfileRequest(
                "I_LIKE",
                "MODEL",
                "openai-main:gpt-4.1-mini",
                "Legal review validation retry repeats.",
                "ERROR",
                new BigDecimal("4"),
                42L,
                "second note"));

        assertThat(response.id()).isEqualTo(21L);
        assertThat(response.hitCount()).isEqualTo(2);
        assertThat(response.severity()).isEqualTo("ERROR");
        assertThat(response.iWeight()).isEqualByComparingTo("4");
        assertThat(response.sourceDailyReportId()).isEqualTo(42L);

        var captor = ArgumentCaptor.forClass(PlatformOpsControlProfile.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
    }

    @Test
    void modelScopeRequiresModelId() {
        assertThatThrownBy(() -> service.create(principal, new CreatePlatformOpsControlProfileRequest(
                "I_LIKE",
                "MODEL",
                null,
                "Repeated model-specific failure.",
                "WARN",
                BigDecimal.ONE,
                null,
                null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("modelId");
    }
}
