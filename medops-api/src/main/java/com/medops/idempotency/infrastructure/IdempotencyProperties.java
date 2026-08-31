package com.medops.idempotency.infrastructure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "medops.idempotency")
public record IdempotencyProperties(@NotNull Duration ttl) {
}
