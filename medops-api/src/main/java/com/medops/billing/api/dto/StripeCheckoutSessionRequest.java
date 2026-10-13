package com.medops.billing.api.dto;

import java.util.Map;

public record StripeCheckoutSessionRequest(
        long amount,
        String currency,
        String productName,
        String idempotencyKey,
        Map<String, String> metadata) {
}
