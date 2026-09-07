package com.medops.auth.controller;

import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.auth.dto.LoginRequest;
import com.medops.auth.dto.UserInfo;
import com.medops.auth.exception.InvalidRefreshTokenException;
import com.medops.auth.security.AuthRateLimitFilter;
import com.medops.auth.security.JwtAuthenticationFilter;
import com.medops.auth.security.JwtService;
import com.medops.auth.service.AuthService;
import com.medops.auth.service.SessionResult;

/**
 * HTTP-level tests for {@link AuthController}: request validation, the {@code ApiResponse}
 * envelope, and error mapping through {@link com.medops.shared.exception.GlobalExceptionHandler}.
 * Security filters are disabled here (see {@code addFilters = false}) since these endpoints are
 * intentionally public - the filter chain's own behaviour is out of scope for this slice.
 */
@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, AuthRateLimitFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    private static final UserInfo SAMPLE_USER = new UserInfo(UUID.randomUUID(), "user@medops.dev", "PATIENT");
    private static final SessionResult SAMPLE_RESPONSE =
            new SessionResult("access-token", "refresh-token", SAMPLE_USER);

    private static final @NonNull MediaType JSON = Objects.requireNonNull(MediaType.APPLICATION_JSON);
    private static final String REFRESH_COOKIE = "MEDOPS_REFRESH";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    private @NonNull String json(Object value) throws Exception {
        return Objects.requireNonNull(objectMapper.writeValueAsString(value));
    }

    @Test
    void login_returns200WithEnvelope() throws Exception {
        when(authService.login(any())).thenReturn(SAMPLE_RESPONSE);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(JSON)
                        .content(json(
                                new LoginRequest("user@medops.dev", "Password123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("user@medops.dev"))
                .andExpect(jsonPath("$.data.role").value("PATIENT"));
    }

    @Test
    void login_returns401_onBadCredentials() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(JSON)
                        .content(json(
                                new LoginRequest("user@medops.dev", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.status").value("UNAUTHENTICATED"));
    }

    @Test
    void refresh_returns200WithEnvelope() throws Exception {
        when(authService.refresh(any())).thenReturn(SAMPLE_RESPONSE);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(REFRESH_COOKIE, "some-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("user@medops.dev"));
    }

    @Test
    void refresh_returns401_onInvalidToken() throws Exception {
        when(authService.refresh(any())).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(REFRESH_COOKIE, "bad-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.status").value("UNAUTHENTICATED"));
    }

    @Test
    void refresh_returns401_whenCookieMissing() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.status").value("UNAUTHENTICATED"));
    }

    @Test
    void logout_returns204() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie(REFRESH_COOKIE, "some-refresh-token")))
                .andExpect(status().isNoContent());
    }

    @Test
    void logout_returns204_whenCookieMissing() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());
    }
}
