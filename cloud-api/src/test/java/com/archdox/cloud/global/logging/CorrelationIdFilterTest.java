package com.archdox.cloud.global.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void keepsValidIncomingCorrelationIdAndClearsMdcAfterRequest() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.addHeader(CorrelationIds.HEADER, "test-correlation-1");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("test-correlation-1", response.getHeader(CorrelationIds.HEADER));
        assertEquals(null, MDC.get(CorrelationIds.MDC_KEY));
    }

    @Test
    void createsCorrelationIdWhenHeaderIsMissing() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/test");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNotNull(response.getHeader(CorrelationIds.HEADER));
    }
}
