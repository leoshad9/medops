package com.medops.billing.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "stripe")
public record StripeProperties(
        String secretKey,
        String publishableKey,
        String webhookSecret,
        String currency,
        String returnUrl) {

    public StripeProperties {
        if (currency == null || currency.isBlank()) {
            currency = "inr";
        }
        if (returnUrl == null || returnUrl.isBlank()) {
            returnUrl = "https://medops.duckdns.org/patient/billing";
        }
    }
}
