package com.archdox.cloud.global.security;

public record UserPrincipal(
        Long userId,
        String email
) {
}
