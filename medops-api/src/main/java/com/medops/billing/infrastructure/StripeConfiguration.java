package com.medops.billing.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.stripe.Stripe;

import jakarta.annotation.PostConstruct;

@Configuration
@EnableConfigurationProperties(StripeProperties.class)
public class StripeConfiguration {

    private final StripeProperties properties;

    public StripeConfiguration(StripeProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (properties.secretKey() != null && !properties.secretKey().isBlank()) {
            Stripe.apiKey = properties.secretKey();
        }
    }
}
