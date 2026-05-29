package com.archdox.cloud.global.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RequestRateLimiterTest {
    @Test
    void rejectsRequestsBeyondWindowLimit() {
        var limiter = new RequestRateLimiter(Clock.fixed(Instant.parse("2026-05-26T00:00:00Z"), ZoneOffset.UTC));
        var rule = new RateLimitProperties.ResolvedRule("auth-login", 2, Duration.ofMinutes(1));

        assertTrue(limiter.consume("auth-login:127.0.0.1", rule, 1000).allowed());
        assertTrue(limiter.consume("auth-login:127.0.0.1", rule, 1000).allowed());
        assertFalse(limiter.consume("auth-login:127.0.0.1", rule, 1000).allowed());
    }
}
