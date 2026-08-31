package com.medops.ratelimit.infrastructure;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.medops.ratelimit.domain.RateLimiterStore;
import com.medops.shared.exception.ServiceUnavailableException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis INCR + EXPIRE fixed-window limiter. Fail closed when Redis is unreachable.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "medops.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public final class RedisRateLimiterStore implements RateLimiterStore {

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
        String redisKey = "ratelimit:" + key;
        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count == null) {
                throw new ServiceUnavailableException("Rate limiter temporarily unavailable");
            }
            if (count == 1L) {
                redisTemplate.expire(redisKey, window);
            }
            return count <= maxAttempts;
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Redis rate limiter unavailable");
            throw new ServiceUnavailableException("Rate limiter temporarily unavailable", ex);
        }
    }
}
