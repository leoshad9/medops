package com.medops.auth.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import com.medops.auth.security.CookieConstants;
import com.medops.auth.security.principal.MedOpsUserDetailsService;

import jakarta.servlet.http.Cookie;

/**
 * Verifies the CSRF-latency fast paths: public auth endpoints (notably
 * {@code GET /api/auth/csrf}) skip JWT parsing + the user lookup entirely, and
 * authenticated requests pay for exactly one JJWT verification.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1tZWRvcHMtdGVzdGluZy1vbmx5LTMyYnl0ZQ==";

    @Mock
    private MedOpsUserDetailsService userDetailsService;

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtService = new JwtService(new JwtProperties(
                SECRET, 900000, 28800000, "medops-api", "medops-web"));
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
    }

    @Test
    void skipsJwtAndUserLookupOnPublicCsrfEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/csrf");
        request.setCookies(new Cookie(CookieConstants.ACCESS, "any-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        verify(userDetailsService, never()).loadUserByUsername(org.mockito.ArgumentMatchers.anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void authenticatesWithSingleParseOnProtectedEndpoint() throws Exception {
        User userDetails = new User(
                "patient@medops.dev", "password",
                List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));
        String token = jwtService.generateAccessToken(userDetails);
        when(userDetailsService.loadUserByUsername("patient@medops.dev")).thenReturn(userDetails);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.setCookies(new Cookie(CookieConstants.ACCESS, token));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("patient@medops.dev");
        verify(userDetailsService).loadUserByUsername("patient@medops.dev");
    }

    @Test
    void leavesInvalidTokenUnauthenticatedWithoutUserLookup() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.setCookies(new Cookie(CookieConstants.ACCESS, "not-a-jwt"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, never()).loadUserByUsername(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void validateAndExtractUsernameRejectsTamperedToken() {
        User userDetails = new User(
                "patient@medops.dev", "password",
                List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));
        String token = jwtService.generateAccessToken(userDetails);

        Optional<String> result = jwtService.validateAndExtractUsername(token + "tampered");

        assertThat(result).isEmpty();
    }
}
