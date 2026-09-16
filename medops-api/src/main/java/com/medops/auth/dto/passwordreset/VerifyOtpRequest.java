package com.medops.auth.dto.passwordreset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyOtpRequest(

        @NotBlank(message = "Reset flow ID is required")
        String resetFlowId,

        @NotBlank(message = "OTP is required")
        @Size(min = 6, max = 6, message = "OTP must be 6 digits")
        @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
        String otp) {
}

