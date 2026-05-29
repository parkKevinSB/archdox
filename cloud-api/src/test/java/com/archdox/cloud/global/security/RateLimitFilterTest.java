package com.archdox.cloud.global.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {
    private final RateLimitProperties properties = new RateLimitProperties();
    private final RateLimitFilter filter = new RateLimitFilter(
            properties,
            new RequestRateLimiter(),
            new ObjectMapper().findAndRegisterModules(),
            new ClientIpResolver(properties));

    @Test
    void rejectsLoginAttemptsBeforeSecurityAndDatabaseWork() throws Exception {
        properties.setLogin(new RateLimitProperties.Rule(1, Duration.ofMinutes(1)));
        var firstChain = mock(FilterChain.class);
        var secondChain = mock(FilterChain.class);
        var firstRequest = post("/api/v1/auth/login", "203.0.113.10");
        var firstResponse = new MockHttpServletResponse();
        var secondRequest = post("/api/v1/auth/login", "203.0.113.10");
        var secondResponse = new MockHttpServletResponse();

        filter.doFilter(firstRequest, firstResponse, firstChain);
        filter.doFilter(secondRequest, secondResponse, secondChain);

        assertEquals(200, firstResponse.getStatus());
        assertEquals(429, secondResponse.getStatus());
        assertEquals("1", secondResponse.getHeader("X-RateLimit-Limit"));
        verify(firstChain).doFilter(firstRequest, firstResponse);
        verify(secondChain, never()).doFilter(secondRequest, secondResponse);
    }

    @Test
    void limitsPlatformAdminApisSeparatelyFromGeneralApiTraffic() throws Exception {
        properties.setPlatformAdmin(new RateLimitProperties.Rule(1, Duration.ofMinutes(1)));
        var adminChain = mock(FilterChain.class);
        var secondAdminChain = mock(FilterChain.class);
        var generalChain = mock(FilterChain.class);
        var adminRequest = get("/api/v1/platform-admin/ops/summary", "203.0.113.11");
        var secondAdminRequest = get("/api/v1/platform-admin/ops/users", "203.0.113.11");
        var generalRequest = get("/api/v1/projects", "203.0.113.11");
        var adminResponse = new MockHttpServletResponse();
        var secondAdminResponse = new MockHttpServletResponse();
        var generalResponse = new MockHttpServletResponse();

        filter.doFilter(adminRequest, adminResponse, adminChain);
        filter.doFilter(secondAdminRequest, secondAdminResponse, secondAdminChain);
        filter.doFilter(generalRequest, generalResponse, generalChain);

        assertEquals(200, adminResponse.getStatus());
        assertEquals(429, secondAdminResponse.getStatus());
        assertEquals(200, generalResponse.getStatus());
        verify(generalChain).doFilter(generalRequest, generalResponse);
    }

    @Test
    void canUseForwardedIpWhenRunningBehindTrustedProxy() throws Exception {
        properties.setUseForwardedHeaders(true);
        properties.setLogin(new RateLimitProperties.Rule(1, Duration.ofMinutes(1)));
        var firstChain = mock(FilterChain.class);
        var secondChain = mock(FilterChain.class);
        var firstRequest = post("/api/v1/auth/login", "10.0.0.10");
        var secondRequest = post("/api/v1/auth/login", "10.0.0.10");
        firstRequest.addHeader("CF-Connecting-IP", "198.51.100.1");
        secondRequest.addHeader("CF-Connecting-IP", "198.51.100.2");
        var firstResponse = new MockHttpServletResponse();
        var secondResponse = new MockHttpServletResponse();

        filter.doFilter(firstRequest, firstResponse, firstChain);
        filter.doFilter(secondRequest, secondResponse, secondChain);

        assertEquals(200, firstResponse.getStatus());
        assertEquals(200, secondResponse.getStatus());
    }

    private MockHttpServletRequest get(String path, String remoteAddress) {
        var request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private MockHttpServletRequest post(String path, String remoteAddress) {
        var request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
