package com.medops.billing.api.dto;

public record StripeWebhookEvent(
        String type,
        String paymentIntentId,
        Long amount,
        String currency,
        java.util.Map<String, String> metadata) {
}
