package com.medops.billing.api.dto;

public record StripeCheckoutSessionResponse(
        String clientSecret,
        String sessionId,
        long amountCents,
        String currency,
        String publishableKey) {
}
