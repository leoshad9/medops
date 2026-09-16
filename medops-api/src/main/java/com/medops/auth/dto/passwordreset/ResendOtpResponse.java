package com.medops.auth.dto.passwordreset;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from resending an OTP for an existing reset flow.
 * Distinguishes success from expired-flow and cooldown cases so the frontend
 * can show appropriate messaging instead of a generic "done".
 */
public record ResendOtpResponse(

        @JsonProperty("status")
        Status status,

        @JsonProperty("message")
        String message) {

    public enum Status {
        /** OTP regenerated and email queued for delivery. */
        SENT,
        /** The flow does not exist or has expired (no OTP in Redis). */
        EXPIRED,
        /** A resend is already in progress; wait before trying again. */
        COOLDOWN
    }
}

