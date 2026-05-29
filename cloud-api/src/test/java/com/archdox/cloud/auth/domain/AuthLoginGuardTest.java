package com.archdox.cloud.auth.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AuthLoginGuardTest {
    @Test
    void locksAfterConfiguredFailuresWithinWindow() {
        var now = OffsetDateTime.parse("2026-05-26T00:00:00Z");
        var guard = new AuthLoginGuard(AuthLoginGuardScope.EMAIL, "hash", "a***@example.com", now);

        assertFalse(guard.recordFailure(now, 2, Duration.ofMinutes(15), Duration.ofMinutes(10)));
        assertTrue(guard.recordFailure(now.plusMinutes(1), 2, Duration.ofMinutes(15), Duration.ofMinutes(10)));

        assertEquals(2, guard.failureCount());
        assertTrue(guard.isLocked(now.plusMinutes(2)));
    }

    @Test
    void resetsFailureCountAfterFailureWindow() {
        var now = OffsetDateTime.parse("2026-05-26T00:00:00Z");
        var guard = new AuthLoginGuard(AuthLoginGuardScope.IP, "hash", "203.0.1***", now);

        guard.recordFailure(now, 2, Duration.ofMinutes(15), Duration.ofMinutes(10));
        var locked = guard.recordFailure(now.plusMinutes(16), 2, Duration.ofMinutes(15), Duration.ofMinutes(10));

        assertFalse(locked);
        assertEquals(1, guard.failureCount());
    }
}
