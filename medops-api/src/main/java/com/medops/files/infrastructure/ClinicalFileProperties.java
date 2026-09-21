package com.medops.files.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "medops.files")
public record ClinicalFileProperties(
        @NotBlank String root,
        long storageQuotaBytes
) {

    public ClinicalFileProperties {
        if (storageQuotaBytes <= 0) {
            throw new IllegalArgumentException(
                    "storageQuotaBytes must be positive, got: " + storageQuotaBytes);
        }
    }
}
