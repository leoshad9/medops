package com.medops.ratelimit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class InMemoryRateLimiterStoreTest {

    @Test
    void tryAcquireBlocksAfterMaxAttempts() {
        InMemoryRateLimiterStore store = new InMemoryRateLimiterStore();
        Duration window = Duration.ofMinutes(1);

        for (int i = 0; i < 10; i++) {
            assertThat(store.tryAcquire("login|127.0.0.1", 10, window)).isTrue();
        }
        assertThat(store.tryAcquire("login|127.0.0.1", 10, window)).isFalse();
        assertThat(store.tryAcquire("login|10.0.0.2", 10, window)).isTrue();
    }
}
