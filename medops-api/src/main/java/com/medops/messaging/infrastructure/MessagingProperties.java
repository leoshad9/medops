package com.medops.messaging.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "medops.messaging")
public record MessagingProperties(
        boolean enabled,
        @NotBlank String appointmentsTopic,
        @NotBlank String reportsTopic
) {
}
