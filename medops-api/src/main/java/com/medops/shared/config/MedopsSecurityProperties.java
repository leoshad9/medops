package com.medops.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "medops.security")
public record MedopsSecurityProperties(
        /** When true, POST /api/v1/patients and /api/v1/doctors are anonymous. */
        boolean openRegistration,
        /** When true, Swagger UI and OpenAPI docs are anonymous. */
        boolean apiDocsPublic) {
}
