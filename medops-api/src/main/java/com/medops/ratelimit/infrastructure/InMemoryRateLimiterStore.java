package com.medops.ratelimit.infrastructure;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.medops.ratelimit.domain.RateLimiterStore;

/**
 * Single-instance limiter used when Redis is disabled (tests and local without compose).
 */
@Component
@ConditionalOnProperty(prefix = "medops.redis", name = "enabled", havingValue = "false")
public final class InMemoryRateLimiterStore implements RateLimiterStore {

    private final Map<String, Window> windowsByClient = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();
        Window bucket = windowsByClient.computeIfAbsent(key, ignored -> new Window(now));
        synchronized (bucket) {
            if (now - bucket.windowStartMillis >= windowMillis) {
                bucket.windowStartMillis = now;
                bucket.count = 0;
            }
            if (bucket.count >= maxAttempts) {
                return false;
            }
            bucket.count++;
            return true;
        }
    }

    private static final class Window {
        private long windowStartMillis;
        private int count;

        private Window(long windowStartMillis) {
            this.windowStartMillis = windowStartMillis;
        }
    }
}
