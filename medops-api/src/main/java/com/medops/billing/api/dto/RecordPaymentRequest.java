package com.medops.billing.api.dto;

import com.medops.billing.domain.PaymentMethod;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RecordPaymentRequest(
        @NotNull @Positive Long amountCents,
        @NotNull PaymentMethod method,
        String reference) {
}
