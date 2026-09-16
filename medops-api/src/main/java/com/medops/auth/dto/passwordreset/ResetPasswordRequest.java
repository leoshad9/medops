package com.medops.auth.dto.passwordreset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of the reset-password call. The reset token is deliberately absent: it is read
 * from the HttpOnly cookie set by the verify-otp step, not sent by the client.
 */
public record ResetPasswordRequest(

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,100}$",
                message = "Password must contain uppercase, lowercase, digit, and special character")
        String newPassword) {
}
