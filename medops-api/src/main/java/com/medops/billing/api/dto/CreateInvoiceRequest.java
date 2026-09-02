package com.medops.billing.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record CreateInvoiceRequest(
        @NotNull UUID patientProfileId,
        UUID appointmentId,
        LocalDate dueDate,
        String notes) {
}
