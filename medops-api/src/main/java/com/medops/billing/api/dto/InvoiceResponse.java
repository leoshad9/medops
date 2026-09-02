package com.medops.billing.api.dto;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import com.medops.billing.domain.InvoiceStatus;

public record InvoiceResponse(
        UUID id,
        UUID patientProfileId,
        UUID appointmentId,
        InvoiceStatus status,
        long totalCents,
        long paidCents,
        long balanceCents,
        LocalDate dueDate,
        String notes,
        List<InvoiceItemResponse> items,
        ZonedDateTime createdAt) {

    public record InvoiceItemResponse(
            UUID id,
            String description,
            int quantity,
            long unitPriceCents,
            long lineTotalCents) {
    }
}
