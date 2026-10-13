package com.medops.billing.api.dto;

public record StripeCheckoutSessionResult(
        String id,
        String clientSecret,
        Long amountTotal,
        String currency,
        Metadata metadata) {

    public record Metadata(
            String invoiceId,
            String patientProfileId,
            String actorUserId,
            String actorEmail) {
    }
}
