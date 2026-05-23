package com.archdox.cloud.auth.dto;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds
) {
    public static AuthTokenResponse bearer(String accessToken, String refreshToken, long expiresInSeconds) {
        return new AuthTokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }
}
