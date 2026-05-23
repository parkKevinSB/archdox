package com.archdox.cloud.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "archdox.jwt")
public record JwtProperties(
        String secret,
        long accessTokenTtlMinutes,
        long refreshTokenTtlDays
) {
}
