package com.archdox.cloud.global.security;

import com.archdox.cloud.engine.auth.application.EngineApiKeyAuthenticationService;
import com.archdox.cloud.global.api.ApiError;
import com.archdox.cloud.global.api.UnauthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class EngineApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private static final String EXTERNAL_ENGINE_PREFIX = "/api/v1/engine/external/";
    private static final String KEY_HEADER = "X-ArchDox-Engine-Key";

    private final EngineApiKeyAuthenticationService service;
    private final ObjectMapper objectMapper;

    public EngineApiKeyAuthenticationFilter(
            EngineApiKeyAuthenticationService service,
            ObjectMapper objectMapper
    ) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(EXTERNAL_ENGINE_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            var principal = service.authenticate(rawKey(request));
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_ENGINE_API")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (UnauthorizedException ex) {
            writeUnauthorized(response, ex);
        }
    }

    private String rawKey(HttpServletRequest request) {
        var headerKey = request.getHeader(KEY_HEADER);
        if (headerKey != null && !headerKey.isBlank()) {
            return headerKey;
        }
        var authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            return "";
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length());
        }
        if (authorization.startsWith("ArchDox ")) {
            return authorization.substring("ArchDox ".length());
        }
        return "";
    }

    private void writeUnauthorized(HttpServletResponse response, UnauthorizedException ex) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(
                HttpServletResponse.SC_UNAUTHORIZED,
                ex.code(),
                ex.messageKey(),
                ex.getMessage(),
                ex.params(),
                List.of()));
    }
}
