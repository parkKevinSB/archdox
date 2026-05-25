package com.archdox.cloud.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var correlationId = normalize(request.getHeader(CorrelationIds.HEADER));
        response.setHeader(CorrelationIds.HEADER, correlationId);
        MDC.put(CorrelationIds.MDC_KEY, correlationId);
        MDC.put("httpMethod", request.getMethod());
        MDC.put("httpPath", request.getRequestURI());
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("httpPath");
            MDC.remove("httpMethod");
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        var normalized = value.trim();
        if (normalized.length() > 120 || !normalized.matches("[A-Za-z0-9._:-]+")) {
            return UUID.randomUUID().toString();
        }
        return normalized;
    }
}
