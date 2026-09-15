package com.medops.auth.dto.passwordreset;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Result of a successful OTP verification.
 * <p>
 * {@code resetToken} is marked {@link JsonIgnore} on purpose: the controller moves it
 * into an HttpOnly cookie, so it must never appear in the response body (and therefore
 * never in the URL the browser navigates to next).
 */
public record VerifyOtpResponse(
        String message,
        @JsonIgnore String resetToken) {
}
