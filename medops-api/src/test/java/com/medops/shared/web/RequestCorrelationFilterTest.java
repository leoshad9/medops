package com.medops.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void preservesGatewayIdentifiersAndAddsThemToTheLoggingContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "request-123");
        request.addHeader(RequestCorrelationFilter.CORRELATION_ID_HEADER, "correlation-456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)).isEqualTo("request-123");
            assertThat(MDC.get(RequestCorrelationFilter.CORRELATION_ID_MDC_KEY)).isEqualTo("correlation-456");
        });

        assertThat(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER)).isEqualTo("request-123");
        assertThat(response.getHeader(RequestCorrelationFilter.CORRELATION_ID_HEADER)).isEqualTo("correlation-456");
        assertThat(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(RequestCorrelationFilter.CORRELATION_ID_MDC_KEY)).isNull();
    }

    @Test
    void generatesARequestIdAndUsesItAsTheDefaultCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            String requestId = MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY);
            assertThat(requestId).isNotBlank();
            assertThat(MDC.get(RequestCorrelationFilter.CORRELATION_ID_MDC_KEY)).isEqualTo(requestId);
        });

        assertThat(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER)).isNotBlank();
        assertThat(response.getHeader(RequestCorrelationFilter.CORRELATION_ID_HEADER))
                .isEqualTo(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER));
    }
}
