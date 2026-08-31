package com.medops.appointments.api.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookAppointmentRequest(
        @NotNull(message = "Doctor is required")
        UUID doctorId,

        @NotNull(message = "Start time is required")
        Instant startsAt,

        @Size(max = 500, message = "Reason must be at most 500 characters")
        String reason
) {
}
