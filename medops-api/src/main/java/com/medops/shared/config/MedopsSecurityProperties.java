package com.medops.shared.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "medops.security")
public record MedopsSecurityProperties(
        /** When true, POST /api/v1/patients and /api/v1/doctors are anonymous. */
        boolean openRegistration,
        /** When true, Swagger UI and OpenAPI docs are anonymous. */
        boolean apiDocsPublic,
        /**
         * CIDR ranges of trusted reverse proxies. Forwarded headers
         * ({@code X-Forwarded-For}, {@code X-Real-IP}) are only honored when the
         * direct connection peer matches one of these prefixes — otherwise the
         * caller-controlled header is ignored, preventing IP-spoofing of rate limits.
         */
        List<String> trustedProxyCidrs) {
}
