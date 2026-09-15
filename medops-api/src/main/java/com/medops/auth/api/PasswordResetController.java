package com.medops.auth.api;

import java.time.Duration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medops.auth.security.PasswordResetProperties;
import com.medops.auth.dto.passwordreset.ForgotPasswordRequest;
import com.medops.auth.dto.passwordreset.ForgotPasswordResponse;
import com.medops.auth.dto.passwordreset.ResetPasswordRequest;
import com.medops.auth.dto.passwordreset.ResetPasswordResponse;
import com.medops.auth.dto.passwordreset.ResendOtpRequest;
import com.medops.auth.dto.passwordreset.ResendOtpResponse;
import com.medops.auth.dto.passwordreset.VerifyOtpRequest;
import com.medops.auth.dto.passwordreset.VerifyOtpResponse;
import com.medops.auth.security.CookieConstants;
import com.medops.auth.application.ForgotPasswordService;
import com.medops.auth.application.ResetPasswordService;
import com.medops.auth.application.ResendOtpService;
import com.medops.auth.application.VerifyOtpService;
import com.medops.shared.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth/password")
@RequiredArgsConstructor
public final class PasswordResetController {

    /**
     * Scoped so the reset-token cookie is only ever sent back to these endpoints
     * instead of on every request.
     */
    private static final String RESET_COOKIE_PATH = "/api/auth/password";

    private final ForgotPasswordService forgotPasswordService;
    private final ResendOtpService resendOtpService;
    private final VerifyOtpService verifyOtpService;
    private final ResetPasswordService resetPasswordService;
    private final PasswordResetProperties passwordResetProperties;

    @PostMapping("/forgot")
    public ResponseEntity<ApiResponse<ForgotPasswordResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        ForgotPasswordResponse response = forgotPasswordService.forgotPassword(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<ResendOtpResponse>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        ResendOtpResponse response = resendOtpService.resendOtp(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<VerifyOtpResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request, HttpServletResponse httpResponse) {
        VerifyOtpResponse response = verifyOtpService.verifyOtp(request);
        if (response.resetToken() != null) {
            // The token leaves the server in an HttpOnly cookie and is excluded from the
            // JSON body (see VerifyOtpResponse#resetToken), so the SPA never has to put
            // it in the URL it navigates to next.
            httpResponse.addHeader(HttpHeaders.SET_COOKIE,
                    passwordResetCookie(response.resetToken()).toString());
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<ResetPasswordResponse>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        // The raw token is read from the cookie rather than the body, so it never
        // appears in a URL, browser history, or the request payload.
        String resetToken = readPasswordResetCookie(httpRequest);
        if (resetToken == null) {
            return ResponseEntity.ok(ApiResponse.success(
                    new ResetPasswordResponse("Invalid or expired reset token.")));
        }
        ResetPasswordResponse response = resetPasswordService.resetPassword(request, resetToken);
        if (isConsumed(response)) {
            // The token is consumed single-use server-side (GETDEL), so once the service
            // reports success or invalid/expired the cookie holds nothing redeemable and
            // must be dropped. On transient failures the cookie is deliberately kept so
            // the user can retry with the still-valid token.
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, clearPasswordResetCookie().toString());
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Reports whether the reset-token cookie holds nothing redeemable anymore.
     *
     * @param response the service result
     * @return {@code true} when the token was consumed or rejected
     */
    private static boolean isConsumed(ResetPasswordResponse response) {
        String message = response.message();
        return message != null
                && (message.contains("reset successfully") || message.contains("Invalid or expired"));
    }

    private ResponseCookie passwordResetCookie(String token) {
        return ResponseCookie.from(CookieConstants.PASSWORD_RESET, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(RESET_COOKIE_PATH)
                .maxAge(passwordResetProperties.resetToken().ttl())
                .build();
    }

    private ResponseCookie clearPasswordResetCookie() {
        return ResponseCookie.from(CookieConstants.PASSWORD_RESET, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(RESET_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }

    private String readPasswordResetCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (CookieConstants.PASSWORD_RESET.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        // X-Forwarded-For is only trustworthy behind a reverse proxy;
        // the original client is the FIRST entry (proxies prepend, not append).
        // We validate the IP format to reject malformed/host spoofed values.
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            String firstIp = xForwardedFor.split(",")[0].trim();
            if (isValidIp(firstIp)) {
                return firstIp;
            }
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && isValidIp(xRealIp)) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    private static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            try {
                for (String part : parts) {
                    int value = Integer.parseInt(part);
                    if (value < 0 || value > 255) {
                        return false;
                    }
                }
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return ip.split(":").length >= 2;
    }
}
