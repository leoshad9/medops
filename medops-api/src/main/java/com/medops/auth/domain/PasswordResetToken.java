package com.medops.auth.domain;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PasswordResetToken(
        UUID userId,
        String resetFlowId,
        boolean used) {

    /** Creates a persisted password-reset token record. */
    @JsonCreator
    public PasswordResetToken(
            @JsonProperty("userId") UUID userId,
            @JsonProperty("resetFlowId") String resetFlowId,
            @JsonProperty("used") boolean used) {
        this.userId = userId;
        this.resetFlowId = resetFlowId;
        this.used = used;
    }

    /** Returns a copy marked as consumed. */
    public PasswordResetToken markUsed() {
        return new PasswordResetToken(userId, resetFlowId, true);
    }
}

