package com.medops.auth.dto.passwordreset;

public record ForgotPasswordResponse(
        String message,
        String resetFlowId) {
}
