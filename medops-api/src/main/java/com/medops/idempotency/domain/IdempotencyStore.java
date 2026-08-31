package com.medops.idempotency.domain;

import java.time.Duration;
import java.util.Optional;

/**
 * Shared store for HTTP idempotency records. Redis in multi-instance deployments;
 * in-memory when Redis is disabled.
 */
public interface IdempotencyStore {

    Optional<IdempotencyRecord> find(String storeKey);

    /**
     * Atomically creates a {@code STARTED} record. Returns {@code false} if the key already exists.
     */
    boolean tryBegin(String storeKey, String requestHash, Duration ttl);

    void complete(String storeKey, String requestHash, String responseJson, Duration ttl);

    void abandon(String storeKey);
}
