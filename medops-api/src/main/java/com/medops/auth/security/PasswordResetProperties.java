package com.medops.auth.security;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties(prefix = "password-reset")
public record PasswordResetProperties(

        // @Valid cascades into the nested records â€” without it their @NotBlank/@Positive
        // constraints are silently skipped and a missing key only surfaces as an NPE
        // at first use (seen as 'secret is null' in PasswordResetCodec).
        @Valid @NotNull Otp otp,
        @Valid @NotNull ResetToken resetToken,
        @Valid @NotNull Resend resend,
        @Valid @NotNull RateLimit rateLimit) {

    @Validated
    public record Otp(
            @NotNull @DurationMin(millis = 1) Duration ttl,
            @Positive int maxAttempts,
            // PasswordResetCodec.generateOtp builds the OTP with int math, which overflows
            // beyond 9 digits; fail fast at startup instead of generating garbage.
            @Positive @Max(9) int length,
            @NotBlank String hmacSecret) {
    }

    @Validated
    public record ResetToken(
            @NotNull @DurationMin(millis = 1) Duration ttl) {
    }

    @Validated
    public record Resend(
            @NotNull @DurationMin(millis = 1) Duration cooldown) {
    }

    @Validated
    public record RateLimit(
            @Positive int forgotPerEmailPerHour,
            @Positive int forgotPerIpPerMinute) {
    }
}
