package com.archdox.cloud.aipolicy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.account.domain.UserAccount;
import com.archdox.cloud.account.infra.UserAccountRepository;
import com.archdox.cloud.aipolicy.domain.AiUserBudgetOverride;
import com.archdox.cloud.aipolicy.dto.CreateAiUserBudgetOverrideRequest;
import com.archdox.cloud.aipolicy.infra.AiUserBudgetOverrideRepository;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.domain.Office;
import com.archdox.cloud.office.infra.OfficeMembershipRepository;
import com.archdox.cloud.office.infra.OfficeRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.shared.MembershipStatus;
import com.archdox.shared.OfficeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AiUserBudgetOverrideServiceTest {
    private final AiUserBudgetOverrideRepository overrideRepository = mock(AiUserBudgetOverrideRepository.class);
    private final OfficeRepository officeRepository = mock(OfficeRepository.class);
    private final UserAccountRepository userRepository = mock(UserAccountRepository.class);
    private final OfficeMembershipRepository membershipRepository = mock(OfficeMembershipRepository.class);
    private final PlatformAdminService platformAdminService = mock(PlatformAdminService.class);
    private final OperationEventService operationEventService = mock(OperationEventService.class);
    private final AiUserBudgetOverrideService service = new AiUserBudgetOverrideService(
            overrideRepository,
            officeRepository,
            userRepository,
            membershipRepository,
            platformAdminService,
            operationEventService);

    @Test
    void createOverrideDisablesExistingActiveOverrideAndRecordsAudit() {
        var principal = new UserPrincipal(7L, "platform@test.co.kr");
        var now = OffsetDateTime.now();
        var office = office(10L, now);
        var user = user(20L, now);
        var existing = new AiUserBudgetOverride(
                10L,
                20L,
                5,
                null,
                null,
                "USD",
                "Previous temporary increase",
                now.plusDays(3),
                7L,
                now.minusDays(1));
        ReflectionTestUtils.setField(existing, "id", 99L);
        var request = new CreateAiUserBudgetOverrideRequest(
                10L,
                20L,
                15,
                10_000L,
                null,
                "usd",
                now.plusDays(7),
                "Need extra legal digest validation");

        when(officeRepository.findById(10L)).thenReturn(Optional.of(office));
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));
        when(membershipRepository.existsByUserIdAndOfficeIdAndStatus(20L, 10L, MembershipStatus.ACTIVE))
                .thenReturn(true);
        when(overrideRepository.findActiveByOfficeIdAndUserId(eq(10L), eq(20L), any()))
                .thenReturn(List.of(existing));
        when(overrideRepository.save(any(AiUserBudgetOverride.class))).thenAnswer(invocation -> {
            var saved = invocation.getArgument(0, AiUserBudgetOverride.class);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });

        var response = service.createOverride(principal, request);

        assertThat(existing.disabledAt()).isNotNull();
        assertThat(existing.disabledByUserId()).isEqualTo(7L);
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.active()).isTrue();
        assertThat(response.dailyCallLimit()).isEqualTo(15);
        assertThat(response.monthlyTokenLimit()).isEqualTo(10_000L);
        assertThat(response.budgetCurrency()).isEqualTo("USD");
        verify(platformAdminService).requirePlatformAdmin(principal);
        verify(operationEventService).record(
                eq(10L),
                eq(OperationEventSeverity.INFO),
                eq("AI_USER_BUDGET_OVERRIDE_CREATED"),
                eq("ai-budget-management"),
                eq("user-ai-budget-override:100"),
                eq("AI_USER_BUDGET_OVERRIDE"),
                eq(100L),
                eq(7L),
                eq(null),
                eq("User AI budget override created."),
                any());
    }

    @Test
    void createOverrideRejectsUserWhoIsNotActiveOfficeMember() {
        var principal = new UserPrincipal(7L, "platform@test.co.kr");
        var now = OffsetDateTime.now();
        var office = office(10L, now);
        var user = user(20L, now);
        var request = new CreateAiUserBudgetOverrideRequest(
                10L,
                20L,
                15,
                null,
                null,
                "USD",
                now.plusDays(7),
                "Need extra legal digest validation");

        when(officeRepository.findById(10L)).thenReturn(Optional.of(office));
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));
        when(membershipRepository.existsByUserIdAndOfficeIdAndStatus(20L, 10L, MembershipStatus.ACTIVE))
                .thenReturn(false);

        assertThatThrownBy(() -> service.createOverride(principal, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("User is not an active member");
        verify(overrideRepository, never()).save(any());
        verify(platformAdminService).requirePlatformAdmin(principal);
    }

    private Office office(Long id, OffsetDateTime now) {
        var office = new Office("personal-001", "Personal Office", OfficeType.PERSONAL, "PERSONAL", now);
        ReflectionTestUtils.setField(office, "id", id);
        return office;
    }

    private UserAccount user(Long id, OffsetDateTime now) {
        var user = new UserAccount("user@test.co.kr", "hash", "User", now);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
