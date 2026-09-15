package com.medops.auth.dto.passwordreset;

import jakarta.validation.constraints.NotBlank;

public record ResendOtpRequest(

        @NotBlank(message = "Reset flow ID is required")
        String resetFlowId) {
}

