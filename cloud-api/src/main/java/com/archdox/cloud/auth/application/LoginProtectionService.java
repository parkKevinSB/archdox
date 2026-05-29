package com.archdox.cloud.auth.application;

import com.archdox.cloud.auth.domain.AuthLoginGuard;
import com.archdox.cloud.auth.domain.AuthLoginGuardScope;
import com.archdox.cloud.auth.infra.AuthLoginGuardRepository;
import com.archdox.cloud.global.api.TooManyRequestsException;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginProtectionService {
    private final LoginProtectionProperties properties;
    private final AuthLoginGuardRepository guardRepository;
    private final OperationEventService operationEventService;

    public LoginProtectionService(
            LoginProtectionProperties properties,
            AuthLoginGuardRepository guardRepository,
            OperationEventService operationEventService
    ) {
        this.properties = properties;
        this.guardRepository = guardRepository;
        this.operationEventService = operationEventService;
    }

    @Transactional(readOnly = true)
    public void assertLoginAllowed(String normalizedEmail, String clientIp) {
        if (!properties.isEnabled()) {
            return;
        }
        var now = OffsetDateTime.now();
        rejectIfLocked(AuthLoginGuardScope.EMAIL, normalizedEmail, now);
        rejectIfLocked(AuthLoginGuardScope.IP, clientIp, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String normalizedEmail, String clientIp, Long userId) {
        if (!properties.isEnabled()) {
            return;
        }
        var now = OffsetDateTime.now();
        var emailGuard = recordFailure(
                AuthLoginGuardScope.EMAIL,
                normalizedEmail,
                properties.safeMaxFailuresPerEmail(),
                now);
        var ipGuard = recordFailure(
                AuthLoginGuardScope.IP,
                clientIp,
                properties.safeMaxFailuresPerIp(),
                now);
        recordEvent(
                OperationEventSeverity.WARN,
                "AUTH_LOGIN_FAILED",
                userId,
                "Login failed.",
                Map.of(
                        "emailHash", keyHash(AuthLoginGuardScope.EMAIL, normalizedEmail),
                        "clientIpHash", keyHash(AuthLoginGuardScope.IP, clientIp),
                        "emailFailureCount", emailGuard.failureCount(),
                        "ipFailureCount", ipGuard.failureCount()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String normalizedEmail, String clientIp, Long userId) {
        if (!properties.isEnabled()) {
            return;
        }
        guardRepository.findByScopeAndGuardKeyHash(
                        AuthLoginGuardScope.EMAIL,
                        keyHash(AuthLoginGuardScope.EMAIL, normalizedEmail))
                .ifPresent(guard -> guard.clearFailures(OffsetDateTime.now()));
        recordEvent(
                OperationEventSeverity.INFO,
                "AUTH_LOGIN_SUCCEEDED",
                userId,
                "Login succeeded.",
                Map.of(
                        "emailHash", keyHash(AuthLoginGuardScope.EMAIL, normalizedEmail),
                        "clientIpHash", keyHash(AuthLoginGuardScope.IP, clientIp)));
    }

    private void rejectIfLocked(AuthLoginGuardScope scope, String key, OffsetDateTime now) {
        guardRepository.findByScopeAndGuardKeyHash(scope, keyHash(scope, key))
                .filter(guard -> guard.isLocked(now))
                .ifPresent(guard -> {
                    recordEvent(
                            OperationEventSeverity.WARN,
                            "AUTH_LOGIN_BLOCKED",
                            null,
                            "Login blocked by temporary lockout.",
                            Map.of(
                                    "scope", scope.name(),
                                    "keyHash", guard.guardKeyHash(),
                                    "lockedUntil", guard.lockedUntil().toString()));
                    throw new TooManyRequestsException(
                            "Too many failed login attempts. Please try again later.");
                });
    }

    private AuthLoginGuard recordFailure(
            AuthLoginGuardScope scope,
            String key,
            int maxFailures,
            OffsetDateTime now
    ) {
        var keyHash = keyHash(scope, key);
        var guard = guardRepository.findByScopeAndGuardKeyHash(scope, keyHash)
                .orElseGet(() -> guardRepository.save(new AuthLoginGuard(
                        scope,
                        keyHash,
                        displayKey(scope, key),
                        now)));
        var locked = guard.recordFailure(
                now,
                maxFailures,
                properties.safeFailureWindow(),
                properties.safeLockDuration());
        if (locked) {
            recordEvent(
                    OperationEventSeverity.WARN,
                    "AUTH_LOGIN_LOCKED",
                    null,
                    "Login guard was temporarily locked.",
                    Map.of(
                            "scope", scope.name(),
                            "keyHash", keyHash,
                            "failureCount", guard.failureCount(),
                            "lockedUntil", guard.lockedUntil().toString()));
        }
        return guard;
    }

    private void recordEvent(
            OperationEventSeverity severity,
            String eventType,
            Long userId,
            String message,
            Map<String, Object> payload
    ) {
        operationEventService.record(
                null,
                severity,
                eventType,
                "auth-login",
                userId == null ? null : "user:" + userId,
                userId == null ? "AUTH_SUBJECT" : "USER_ACCOUNT",
                userId,
                userId,
                null,
                message,
                payload);
    }

    private String keyHash(AuthLoginGuardScope scope, String key) {
        try {
            var normalized = scope.name() + ":" + (key == null ? "unknown" : key.trim().toLowerCase());
            var digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash login guard key", ex);
        }
    }

    private String displayKey(AuthLoginGuardScope scope, String key) {
        if (key == null || key.isBlank()) {
            return "unknown";
        }
        var normalized = key.trim().toLowerCase();
        if (scope == AuthLoginGuardScope.EMAIL) {
            var at = normalized.indexOf('@');
            if (at <= 1) {
                return "***";
            }
            return normalized.charAt(0) + "***" + normalized.substring(at);
        }
        return normalized.length() <= 7 ? normalized : normalized.substring(0, 7) + "***";
    }
}
