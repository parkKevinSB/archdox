package com.archdox.agent.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AgentCorrelationIdFilterTest {
    private final AgentCorrelationIdFilter filter = new AgentCorrelationIdFilter();

    @Test
    void keepsValidIncomingCorrelationIdAndClearsMdcAfterRequest() throws Exception {
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        request.addHeader("X-Correlation-Id", "agent-correlation-1");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("agent-correlation-1", response.getHeader("X-Correlation-Id"));
        assertEquals(null, MDC.get("correlationId"));
    }

    @Test
    void createsCorrelationIdWhenHeaderIsMissing() throws Exception {
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNotNull(response.getHeader("X-Correlation-Id"));
    }
}
