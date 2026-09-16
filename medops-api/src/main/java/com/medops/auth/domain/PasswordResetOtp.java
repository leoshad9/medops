package com.medops.auth.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PasswordResetOtp(
        String otpHash,
        int attempts) {

    /** Creates a persisted OTP record. */
    @JsonCreator
    public PasswordResetOtp(
            @JsonProperty("otpHash") String otpHash,
            @JsonProperty("attempts") int attempts) {
        this.otpHash = otpHash;
        this.attempts = attempts;
    }

    /** Returns a copy with its failed-attempt count incremented. */
    public PasswordResetOtp incrementAttempts() {
        return new PasswordResetOtp(otpHash, attempts + 1);
    }
}

