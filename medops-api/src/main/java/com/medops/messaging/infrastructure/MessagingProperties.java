package com.medops.messaging.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties(prefix = "medops.messaging")
public record MessagingProperties(
        boolean enabled,
        @NotBlank String appointmentsTopic,
        @NotBlank String reportsTopic,
        @Positive @DefaultValue("100") int outboxBatchSize,
        @Positive @DefaultValue("1000") long outboxFixedDelayMs,
        @Positive @DefaultValue("3") int consumerRetryAttempts,
        @Positive @DefaultValue("1000") long consumerRetryBackoffMs
) {
}
