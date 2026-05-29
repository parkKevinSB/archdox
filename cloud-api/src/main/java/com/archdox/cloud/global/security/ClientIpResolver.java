package com.archdox.cloud.global.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {
    private final RateLimitProperties properties;

    public ClientIpResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        if (properties.isUseForwardedHeaders()) {
            var cloudflareIp = normalizeIp(request.getHeader("CF-Connecting-IP"));
            if (cloudflareIp != null) {
                return cloudflareIp;
            }
            var realIp = normalizeIp(request.getHeader("X-Real-IP"));
            if (realIp != null) {
                return realIp;
            }
            var forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                var first = forwardedFor.split(",")[0];
                var normalized = normalizeIp(first);
                if (normalized != null) {
                    return normalized;
                }
            }
        }
        var remoteAddress = normalizeIp(request.getRemoteAddr());
        return remoteAddress == null ? "unknown" : remoteAddress;
    }

    private String normalizeIp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 80 || !normalized.matches("[a-f0-9:.%-]+")) {
            return null;
        }
        return normalized;
    }
}
