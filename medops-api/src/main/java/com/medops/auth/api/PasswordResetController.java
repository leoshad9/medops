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
import com.medops.shared.config.MedopsSecurityProperties;
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
    private final MedopsSecurityProperties securityProperties;

    /** Starts a password-recovery flow without disclosing account existence. */
    @PostMapping("/forgot")
    public ResponseEntity<ApiResponse<ForgotPasswordResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        ForgotPasswordResponse response = forgotPasswordService.forgotPassword(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** Requests a replacement OTP for an existing recovery flow. */
    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<ResendOtpResponse>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        ResendOtpResponse response = resendOtpService.resendOtp(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** Verifies an OTP and stores the resulting reset token in a secure cookie. */
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

    /** Resets a password using the verified token cookie. */
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

    /** Builds the secure cookie that carries a password-reset token. */
    private ResponseCookie passwordResetCookie(String token) {
        return ResponseCookie.from(CookieConstants.PASSWORD_RESET, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(RESET_COOKIE_PATH)
                .maxAge(passwordResetProperties.resetToken().ttl())
                .build();
    }

    /** Builds an expired cookie that clears the password-reset token. */
    private ResponseCookie clearPasswordResetCookie() {
        return ResponseCookie.from(CookieConstants.PASSWORD_RESET, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(RESET_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }

    /** Reads the password-reset token from the request cookie. */
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

    /** Returns the trusted client address used for rate limiting. */
    private String getClientIp(HttpServletRequest request) {
        // X-Forwarded-For is only trustworthy when the direct connection peer is a
        // configured reverse proxy. Without this check a caller could spoof the header
        // to bypass per-IP rate limiting on the forgot-password flow.
        String remoteAddr = request.getRemoteAddr();
        if (isTrustedProxy(remoteAddr)) {
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
        }
        return remoteAddr;
    }

    /**
     * Checks whether the given address matches any configured trusted-proxy CIDR.
     *
     * @param remoteAddr the direct connection peer IP
     * @return {@code true} when the address falls inside a configured CIDR range
     */
    private boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || securityProperties.trustedProxyCidrs() == null
                || securityProperties.trustedProxyCidrs().isEmpty()) {
            return false;
        }
        for (String cidr : securityProperties.trustedProxyCidrs()) {
            if (cidrContains(cidr, remoteAddr)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tests whether an IP address falls inside a CIDR or literal-address range.
     *
     * @param cidr the configured CIDR (e.g. {@code 127.0.0.1/8} or {@code 10.0.0.0/8})
     * @param ip the address to test
     * @return {@code true} when the IP is covered by the CIDR
     */
    private static boolean cidrContains(String cidr, String ip) {
        if (cidr == null || ip == null) {
            return false;
        }
        if (!cidr.contains("/")) {
            return ip.trim().equals(cidr);
        }
        String[] parts = cidr.trim().split("/", -1);
        if (parts.length != 2) {
            return false;
        }
        int prefixLen;
        try {
            prefixLen = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        String network = parts[0].trim();
        String address = ip.trim();
        return ipv4InSubnet(network, prefixLen, address)
                || ipv6InSubnet(network, prefixLen, address);
    }

    /** Checks whether an IPv4 address belongs to a configured subnet. */
    private static boolean ipv4InSubnet(String network, int prefixLen, String ip) {
        if (prefixLen < 0 || prefixLen > 32) {
            return false;
        }
        byte[] networkBytes = parseIpv4(network);
        byte[] addressBytes = parseIpv4(ip);
        if (networkBytes == null || addressBytes == null) {
            return false;
        }
        int netmask = prefixLen == 0 ? 0 : -1 << (32 - prefixLen);
        return (ipv4ToInt(networkBytes) & netmask) == (ipv4ToInt(addressBytes) & netmask);
    }

    /** Parses a dotted-decimal IPv4 address into four octets. */
    private static byte[] parseIpv4(String address) {
        String[] octets = address.split("\\.", -1);
        if (octets.length != 4) {
            return null;
        }
        byte[] result = new byte[4];
        for (int i = 0; i < octets.length; i++) {
            int value;
            try {
                value = Integer.parseInt(octets[i]);
            } catch (NumberFormatException e) {
                return null;
            }
            if (value < 0 || value > 255) {
                return null;
            }
            result[i] = (byte) value;
        }
        return result;
    }

    /** Converts four IPv4 octets into their integer representation. */
    private static int ipv4ToInt(byte[] address) {
        int result = 0;
        for (byte octet : address) {
            result = (result << 8) | Byte.toUnsignedInt(octet);
        }
        return result;
    }

    /** Rejects IPv6 CIDR matches until IPv6 subnet comparison is supported. */
    private static boolean ipv6InSubnet(String network, int prefixLen, String ip) {
        return false;
    }

    /** Checks whether a value is a valid IPv4 or IPv6 address. */
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
