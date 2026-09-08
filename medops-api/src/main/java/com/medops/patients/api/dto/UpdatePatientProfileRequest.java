package com.medops.patients.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Editable contact and medical-profile fields for an existing patient.
 * Identity fields (email, MRN, date of birth, gender) are intentionally absent:
 * they are set at registration and cannot be self-served.
 */
public record UpdatePatientProfileRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 255, message = "Full name must be at most 255 characters")
        String fullName,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone number must be 7-15 digits, optionally prefixed with +")
        String phoneNumber,

        @Size(max = 10, message = "Blood group must be at most 10 characters")
        String bloodGroup,

        @Size(max = 255, message = "Address must be at most 255 characters")
        String address,

        @Size(max = 255, message = "Emergency contact must be at most 255 characters")
        String emergencyContact,

        @Size(max = 100, message = "Insurance provider must be at most 100 characters")
        String insuranceProvider,

        @Size(max = 100, message = "Insurance policy number must be at most 100 characters")
        String insurancePolicyNumber
) {
}
