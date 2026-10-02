package com.medops.cache.infrastructure;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/** Atomic Redis scripts used by shared fixed-window counters and cache generations. */
public final class RedisRateLimitScripts {

    private RedisRateLimitScripts() {
    }

    public static final RedisScript<Long> INCREMENT_WITH_EXPIRY = new DefaultRedisScript<>(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """,
            Long.class);

    public static final RedisScript<Long> ADVANCE_GENERATION = new DefaultRedisScript<>(
            "return redis.call('INCR', KEYS[1])", Long.class);
}
