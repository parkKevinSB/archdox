package com.archdox.cloud.global.security;

import com.archdox.cloud.global.api.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimitProperties properties;
    private final RequestRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;

    public RateLimitFilter(
            RateLimitProperties properties,
            RequestRateLimiter rateLimiter,
            ObjectMapper objectMapper,
            ClientIpResolver clientIpResolver
    ) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        var rule = resolveRule(request);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }
        var decision = rateLimiter.consume(
                clientKey(request, rule.name()),
                rule,
                properties.safeMaxTrackedKeys());
        setRateLimitHeaders(response, decision);
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        reject(response, rule, decision);
    }

    private RateLimitProperties.ResolvedRule resolveRule(HttpServletRequest request) {
        var method = request.getMethod();
        var path = normalizePath(request);
        if (isPost(method) && path.equals("/api/v1/auth/login")) {
            return properties.getLogin().resolve("auth-login");
        }
        if (isPost(method) && path.equals("/api/v1/auth/signup")) {
            return properties.getSignup().resolve("auth-signup");
        }
        if (isPost(method) && path.equals("/api/v1/auth/refresh")) {
            return properties.getRefresh().resolve("auth-refresh");
        }
        if (path.equals("/agent/ws")) {
            return properties.getAgentWebsocket().resolve("agent-ws");
        }
        if (startsWithPath(path, "/agent/api")) {
            return properties.getAgentApi().resolve("agent-api");
        }
        if (isUploadPath(method, path)) {
            return properties.getUpload().resolve("upload");
        }
        if (isDocumentGenerationPath(method, path)) {
            return properties.getDocumentGeneration().resolve("document-generation");
        }
        if (startsWithPath(path, "/api/v1/platform-admin")) {
            return properties.getPlatformAdmin().resolve("platform-admin");
        }
        if (startsWithPath(path, "/api/v1/office-ops") || startsWithPath(path, "/api/v1/operation-events")) {
            return properties.getOfficeOps().resolve("office-ops");
        }
        if (startsWithPath(path, "/api/v1")) {
            return properties.getApi().resolve("api");
        }
        return null;
    }

    private boolean isUploadPath(String method, String path) {
        return ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))
                && (path.contains("/content")
                || path.contains("/upload")
                || path.contains("/upload-intents")
                || startsWithPath(path, "/api/v1/photos")
                || startsWithPath(path, "/api/v1/document-templates"));
    }

    private boolean isDocumentGenerationPath(String method, String path) {
        return isPost(method)
                && (startsWithPath(path, "/api/v1/document-jobs")
                || path.endsWith("/document-jobs")
                || path.endsWith("/generate-document"));
    }

    private boolean isPost(String method) {
        return "POST".equalsIgnoreCase(method);
    }

    private boolean startsWithPath(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private String normalizePath(HttpServletRequest request) {
        var uri = request.getRequestURI();
        var contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return uri.isBlank() ? "/" : uri;
    }

    private String clientKey(HttpServletRequest request, String ruleName) {
        return ruleName + ":" + clientIpResolver.resolve(request);
    }

    private void setRateLimitHeaders(HttpServletResponse response, RequestRateLimiter.Decision decision) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetAtMillis() / 1000));
    }

    private void reject(
            HttpServletResponse response,
            RateLimitProperties.ResolvedRule rule,
            RequestRateLimiter.Decision decision
    ) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiError.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "RATE_LIMITED",
                "errors.rateLimited",
                "Too many requests. Please try again later.",
                Map.of(
                        "limitGroup", rule.name(),
                        "retryAfterSeconds", decision.retryAfterSeconds()),
                List.of()));
    }
}
