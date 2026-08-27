package com.medops.auth.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Verifies that security failures raised inside the filter chain are rendered as the
 * standard {@code ErrorResponse} envelope with the correct status, rather than Spring
 * Security's default empty body.
 */
class SecurityErrorHandlersTest {

    private final SecurityErrorWriter errorWriter = new SecurityErrorWriter(new ObjectMapper());

    @Test
    void entryPointReturns401WithErrorEnvelope() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAuthenticationEntryPoint(errorWriter).commence(
                new MockHttpServletRequest(), response, new InsufficientAuthenticationException("no token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("\"success\":false")
                .contains("\"code\":401")
                .contains("\"status\":\"UNAUTHENTICATED\"");
    }

    @Test
    void accessDeniedHandlerReturns403WithErrorEnvelope() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAccessDeniedHandler(errorWriter).handle(
                new MockHttpServletRequest(), response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("\"success\":false")
                .contains("\"code\":403")
                .contains("\"status\":\"PERMISSION_DENIED\"");
    }

    @Test
    void entryPointDoesNotLeakWhyAuthenticationFailed() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAuthenticationEntryPoint(errorWriter).commence(
                new MockHttpServletRequest(), response,
                new InsufficientAuthenticationException("JWT expired at 2026-01-01 for user@medops.dev"));

        assertThat(response.getContentAsString())
                .doesNotContain("expired")
                .doesNotContain("user@medops.dev");
    }
}
