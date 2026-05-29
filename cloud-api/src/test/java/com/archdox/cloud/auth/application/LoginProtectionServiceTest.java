package com.archdox.cloud.auth.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.auth.domain.AuthLoginGuard;
import com.archdox.cloud.auth.domain.AuthLoginGuardScope;
import com.archdox.cloud.auth.infra.AuthLoginGuardRepository;
import com.archdox.cloud.global.api.TooManyRequestsException;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginProtectionServiceTest {
    @Mock
    AuthLoginGuardRepository guardRepository;

    @Mock
    OperationEventService operationEventService;

    @Test
    void rejectsLockedEmailBeforePasswordVerification() {
        var properties = new LoginProtectionProperties();
        properties.setLockDuration(Duration.ofMinutes(15));
        var lockedGuard = new AuthLoginGuard(
                AuthLoginGuardScope.EMAIL,
                "email-hash",
                "u***@example.com",
                OffsetDateTime.now());
        lockedGuard.recordFailure(OffsetDateTime.now(), 1, Duration.ofMinutes(15), Duration.ofMinutes(15));
        when(guardRepository.findByScopeAndGuardKeyHash(eq(AuthLoginGuardScope.EMAIL), anyString()))
                .thenReturn(Optional.of(lockedGuard));
        var service = new LoginProtectionService(properties, guardRepository, operationEventService);

        assertThrows(
                TooManyRequestsException.class,
                () -> service.assertLoginAllowed("user@example.com", "203.0.113.10"));

        verify(operationEventService).record(
                eq(null),
                eq(OperationEventSeverity.WARN),
                eq("AUTH_LOGIN_BLOCKED"),
                eq("auth-login"),
                eq(null),
                eq("AUTH_SUBJECT"),
                eq(null),
                eq(null),
                eq(null),
                eq("Login blocked by temporary lockout."),
                org.mockito.ArgumentMatchers.<Map<String, Object>>any());
    }
}
