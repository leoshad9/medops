package com.medops.appointments.api.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

public record RescheduleAppointmentRequest(
        @NotNull(message = "Start time is required")
        Instant startsAt
) {
}
