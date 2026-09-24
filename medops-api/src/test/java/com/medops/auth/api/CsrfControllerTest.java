package com.medops.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;

import com.medops.shared.response.ApiResponse;

import jakarta.servlet.http.Cookie;

/**
 * Guards the CSRF-latency fix: an existing cookie token is reused instead of
 * rotating on every {@code GET /api/auth/csrf} (avoids redundant SecureRandom
 * + Set-Cookie churn on prefetch calls).
 */
@ExtendWith(MockitoExtension.class)
class CsrfControllerTest {

    @Mock
    private CookieCsrfTokenRepository csrfTokenRepository;

    @InjectMocks
    private CsrfController controller;

    @Test
    void reusesExistingTokenInsteadOfRotating() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/csrf");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken existing = new org.springframework.security.web.csrf.DefaultCsrfToken(
                "X-XSRF-TOKEN", "_csrf", "existing-token");
        when(csrfTokenRepository.loadToken(any())).thenReturn(existing);

        ResponseEntity<ApiResponse<Void>> result = controller.csrf(request, response);

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        verify(csrfTokenRepository, never()).generateToken(any());
        verify(csrfTokenRepository, never()).saveToken(any(), any(), any());
    }

    @Test
    void generatesAndSavesTokenWhenNoneExists() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/csrf");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken fresh = new org.springframework.security.web.csrf.DefaultCsrfToken(
                "X-XSRF-TOKEN", "_csrf", "fresh-token");
        when(csrfTokenRepository.loadToken(any())).thenReturn(null);
        when(csrfTokenRepository.generateToken(any())).thenReturn(fresh);

        ResponseEntity<ApiResponse<Void>> result = controller.csrf(request, response);

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        verify(csrfTokenRepository).saveToken(fresh, request, response);
    }

    @Test
    void loadTokenReadsExistingCookieToken() {
        CookieCsrfTokenRepository realRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/csrf");
        request.setCookies(new Cookie("XSRF-TOKEN", "cookie-token"));

        CsrfToken loaded = realRepository.loadToken(request);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getToken()).isEqualTo("cookie-token");
    }
}
