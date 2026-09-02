package com.medops.billing.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddInvoiceItemRequest(
        @NotBlank String description,
        @NotNull @Min(1) Integer quantity,
        @NotNull @Positive Long unitPriceCents) {
}
