package com.medops.prescriptions.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePrescriptionRequest(
        @NotBlank(message = "Medication name is required")
        @Size(max = 200)
        String medicationName,

        @NotBlank(message = "Dosage is required")
        @Size(max = 200)
        String dosage,

        @NotBlank(message = "Instructions are required")
        @Size(max = 1000)
        String instructions,

        @Min(0)
        @Max(24)
        int refillsRemaining
) {
}
