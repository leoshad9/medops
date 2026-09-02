package com.medops.billing.api.dto;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import com.medops.billing.domain.PaymentMethod;
import com.medops.billing.domain.PaymentStatus;

public record PaymentResponse(
        UUID id,
        UUID invoiceId,
        long amountCents,
        PaymentMethod method,
        PaymentStatus status,
        String reference,
        Instant paidAt,
        ZonedDateTime createdAt) {
}
