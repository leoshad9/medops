package com.medops.idempotency.domain;

/**
 * Persisted idempotency state. {@code responseJson} is set only when {@link #COMPLETED}.
 */
public record IdempotencyRecord(Status status, String requestHash, String responseJson) {

    public enum Status {
        STARTED,
        COMPLETED
    }

    public static IdempotencyRecord started(String requestHash) {
        return new IdempotencyRecord(Status.STARTED, requestHash, null);
    }

    public static IdempotencyRecord completed(String requestHash, String responseJson) {
        return new IdempotencyRecord(Status.COMPLETED, requestHash, responseJson);
    }
}
