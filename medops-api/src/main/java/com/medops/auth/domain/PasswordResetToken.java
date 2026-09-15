package com.medops.auth.domain;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PasswordResetToken(
        UUID userId,
        String resetFlowId,
        boolean used) {

    @JsonCreator
    public PasswordResetToken(
            @JsonProperty("userId") UUID userId,
            @JsonProperty("resetFlowId") String resetFlowId,
            @JsonProperty("used") boolean used) {
        this.userId = userId;
        this.resetFlowId = resetFlowId;
        this.used = used;
    }

    public PasswordResetToken markUsed() {
        return new PasswordResetToken(userId, resetFlowId, true);
    }
}

