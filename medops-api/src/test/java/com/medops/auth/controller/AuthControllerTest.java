package com.medops.auth.controller;

import java.util.Objects;

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
import com.medops.auth.dto.AuthResponse;
import com.medops.auth.dto.LoginRequest;
import com.medops.auth.dto.RefreshRequest;
import com.medops.auth.exception.InvalidRefreshTokenException;
import com.medops.auth.security.AuthRateLimitFilter;
import com.medops.auth.security.JwtAuthenticationFilter;
import com.medops.auth.service.AuthService;

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

    private static final AuthResponse SAMPLE_RESPONSE =
            new AuthResponse("access-token", "refresh-token", "Bearer", 900L);

    private static final @NonNull MediaType JSON = Objects.requireNonNull(MediaType.APPLICATION_JSON);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

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
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
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
                        .contentType(JSON)
                        .content(json(new RefreshRequest("some-refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));
    }

    @Test
    void refresh_returns401_onInvalidToken() throws Exception {
        when(authService.refresh(any())).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(JSON)
                        .content(json(new RefreshRequest("bad-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.status").value("UNAUTHENTICATED"));
    }

    @Test
    void logout_returns204() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(JSON)
                        .content(json(new RefreshRequest("some-refresh-token"))))
                .andExpect(status().isNoContent());
    }
}
