package com.medops.files.domain;

public record StoredFile(
        String storageKey,
        String originalFilename,
        String contentType,
        long sizeBytes
) {
}
