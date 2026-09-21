package com.medops.files.domain;

import com.medops.shared.exception.InvalidRequestException;

/**
 * Thrown when a file upload would push the owning user's storage over the
 * configured quota. Mapped to 400 Bad Request by {@code GlobalExceptionHandler}.
 */
public class StorageQuotaExceededException extends InvalidRequestException {

    private final long maxBytes;

    public StorageQuotaExceededException(long usedBytes, long maxBytes) {
        super(String.format(
                "Storage quota exceeded: %d bytes used, limit is %d bytes",
                usedBytes, maxBytes));
        this.maxBytes = maxBytes;
    }

    public StorageQuotaExceededException(long maxBytes) {
        super(String.format("Storage quota exceeded: limit is %d bytes", maxBytes));
        this.maxBytes = maxBytes;
    }

    public long getMaxBytes() {
        return maxBytes;
    }
}
