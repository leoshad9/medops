package com.medops.ratelimit.domain;

import java.time.Duration;

/**
 * Shared fixed-window rate-limit counter. Implementations must fail closed when the store is
 * unavailable so auth and other limited POSTs are not left unprotected.
 */
public interface RateLimiterStore {

    /**
     * @return {@code true} if the caller may proceed; {@code false} if the window is exhausted
     */
    boolean tryAcquire(String key, int maxAttempts, Duration window);
}
