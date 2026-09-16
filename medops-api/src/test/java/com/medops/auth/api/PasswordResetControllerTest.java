package com.medops.auth.api;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.auth.security.PasswordResetProperties;
import com.medops.auth.dto.passwordreset.ForgotPasswordRequest;
import com.medops.auth.dto.passwordreset.ForgotPasswordResponse;
import com.medops.auth.dto.passwordreset.ResetPasswordRequest;
import com.medops.auth.dto.passwordreset.ResetPasswordResponse;
import com.medops.auth.dto.passwordreset.VerifyOtpRequest;
import com.medops.auth.dto.passwordreset.VerifyOtpResponse;
import com.medops.auth.security.filters.AuthRateLimitFilter;
import com.medops.auth.security.CookieConstants;
import com.medops.auth.security.jwt.JwtAuthenticationFilter;
import com.medops.auth.application.ForgotPasswordService;
import com.medops.auth.application.ResetPasswordService;
import com.medops.auth.application.ResendOtpService;
import com.medops.auth.application.VerifyOtpService;
import com.medops.shared.config.MedopsSecurityProperties;

import jakarta.servlet.http.Cookie;

/**
 * HTTP-level tests for {@link PasswordResetController}, focused on how the reset token
 * travels: it must leave the server in an HttpOnly cookie and must never appear in the
 * response body, since the body is what previously carried it into the page URL.
 */
@WebMvcTest(
        controllers = PasswordResetController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, AuthRateLimitFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@Import(PasswordResetControllerTest.ResetPropertiesConfig.class)
class PasswordResetControllerTest {

    private static final String RAW_RESET_TOKEN = "raw-reset-token-abc123";
    private static final String FLOW_ID = UUID.randomUUID().toString();
    private static final String NEW_PASSWORD = "NewPassword123!";

    private static final @NonNull MediaType JSON = Objects.requireNonNull(MediaType.APPLICATION_JSON);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ForgotPasswordService forgotPasswordService;
    @MockitoBean
    private ResendOtpService resendOtpService;
    @MockitoBean
    private VerifyOtpService verifyOtpService;
    @MockitoBean
    private ResetPasswordService resetPasswordService;

    /** Real (non-mocked) properties so the cookie max-age comes from config, not a stub. */
    @TestConfiguration
    static class ResetPropertiesConfig {
        /** Provides password-reset properties for controller tests. */
        @Bean
        PasswordResetProperties passwordResetProperties() {
            return new PasswordResetProperties(
                    new PasswordResetProperties.Otp(Duration.ofMinutes(10), 5, 6, "test-hmac-secret"),
                    new PasswordResetProperties.ResetToken(Duration.ofMinutes(15)),
                    new PasswordResetProperties.Resend(Duration.ofSeconds(60)),
                    new PasswordResetProperties.RateLimit(3, 10));
        }

        /** Provides trusted-proxy security properties for controller tests. */
        @Bean
        MedopsSecurityProperties medopsSecurityProperties() {
            return new MedopsSecurityProperties(true, true,
                    List.of("127.0.0.1/8", "0:0:0:0:0:0:0:1/128", "::1/128"));
        }
    }

    /** Verifies that verify otp sets http only cookie and keeps token out of the body. */
    @Test
    void verifyOtp_setsHttpOnlyCookie_andKeepsTokenOutOfTheBody() throws Exception {
        when(verifyOtpService.verifyOtp(any()))
                .thenReturn(new VerifyOtpResponse("OTP verified successfully.", RAW_RESET_TOKEN));

        mockMvc.perform(post("/api/auth/password/verify-otp")
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(new VerifyOtpRequest(FLOW_ID, "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("OTP verified successfully."))
                .andExpect(jsonPath("$.data.resetToken").doesNotExist())
                .andExpect(content().string(not(containsString(RAW_RESET_TOKEN))))
                .andExpect(cookie().exists(CookieConstants.PASSWORD_RESET))
                .andExpect(cookie().value(CookieConstants.PASSWORD_RESET, RAW_RESET_TOKEN))
                .andExpect(cookie().httpOnly(CookieConstants.PASSWORD_RESET, true))
                .andExpect(cookie().path(CookieConstants.PASSWORD_RESET, "/api/auth/password"));
    }

    /** Verifies that forgot password ignores forwarded ip from peer outside trusted cidr. */
    @Test
    void forgotPassword_ignoresForwardedIpFromPeerOutsideTrustedCidr() throws Exception {
        String remoteAddr = "203.0.113.7";
        ForgotPasswordRequest request = new ForgotPasswordRequest("patient@medops.dev");
        when(forgotPasswordService.forgotPassword(any(), eq(remoteAddr)))
                .thenReturn(new ForgotPasswordResponse("If that email exists, an OTP has been sent.", null));

        mockMvc.perform(post("/api/auth/password/forgot")
                        .with(httpRequest -> {
                            httpRequest.setRemoteAddr(remoteAddr);
                            return httpRequest;
                        })
                        .header("X-Forwarded-For", "198.51.100.25")
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(forgotPasswordService).forgotPassword(any(), eq(remoteAddr));
    }

    /** Verifies that reset password reads token from cookie and clears it. */
    @Test
    void resetPassword_readsTokenFromCookie_andClearsIt() throws Exception {
        when(resetPasswordService.resetPassword(any(), eq(RAW_RESET_TOKEN)))
                .thenReturn(new ResetPasswordResponse("Password reset successfully. Please log in again."));

        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(JSON)
                        .cookie(new Cookie(CookieConstants.PASSWORD_RESET, RAW_RESET_TOKEN))
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest(NEW_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value(containsString("reset successfully")))
                .andExpect(cookie().maxAge(CookieConstants.PASSWORD_RESET, 0));

        verify(resetPasswordService).resetPassword(any(), eq(RAW_RESET_TOKEN));
    }

    /** Verifies that reset password without cookie is rejected and never reaches the service. */
    @Test
    void resetPassword_withoutCookie_isRejected_andNeverReachesTheService() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest(NEW_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Invalid or expired reset token."));

        verify(resetPasswordService, never()).resetPassword(any(), anyString());
    }

    /** Verifies that reset password with weak password returns bad request. */
    @Test
    void resetPassword_withWeakPassword_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(JSON)
                        .cookie(new Cookie(CookieConstants.PASSWORD_RESET, RAW_RESET_TOKEN))
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest("weak"))))
                .andExpect(status().isBadRequest());

        verify(resetPasswordService, never()).resetPassword(any(), anyString());
    }

    /** Verifies that reset password keeps cookie when service fails transiently. */
    @Test
    void resetPassword_keepsCookie_whenServiceFailsTransiently() throws Exception {
        when(resetPasswordService.resetPassword(any(), eq(RAW_RESET_TOKEN)))
                .thenReturn(new ResetPasswordResponse(
                        "Verification service is temporarily unavailable. Please try again."));

        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(JSON)
                        .cookie(new Cookie(CookieConstants.PASSWORD_RESET, RAW_RESET_TOKEN))
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest(NEW_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value(containsString("temporarily unavailable")))
                .andExpect(cookie().doesNotExist(CookieConstants.PASSWORD_RESET));

        verify(resetPasswordService).resetPassword(any(), eq(RAW_RESET_TOKEN));
    }

    /** Verifies that reset password clears cookie when token rejected. */
    @Test
    void resetPassword_clearsCookie_whenTokenRejected() throws Exception {
        when(resetPasswordService.resetPassword(any(), eq(RAW_RESET_TOKEN)))
                .thenReturn(new ResetPasswordResponse("Invalid or expired reset token."));

        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(JSON)
                        .cookie(new Cookie(CookieConstants.PASSWORD_RESET, RAW_RESET_TOKEN))
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest(NEW_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(CookieConstants.PASSWORD_RESET, 0));

        verify(resetPasswordService).resetPassword(any(), eq(RAW_RESET_TOKEN));
    }
}
