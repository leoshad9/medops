package com.medops.cache.infrastructure;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.medops.cache.domain.ReportSummaryCache;
import com.medops.reports.infrastructure.AiClientProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "medops.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public final class RedisReportSummaryCache implements ReportSummaryCache {

    private final StringRedisTemplate redisTemplate;
    private final AiClientProperties aiProperties;

    @Override
    public Optional<String> get(UUID reportId) {
        try {
            String value = redisTemplate.opsForValue().get(key(reportId));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (RuntimeException ex) {
            log.warn("Report summary cache read failed; falling back to database");
            return Optional.empty();
        }
    }

    @Override
    public void put(UUID reportId, String summary) {
        try {
            Duration ttl = aiProperties.summaryCacheTtl();
            redisTemplate.opsForValue().set(key(reportId), summary, ttl);
        } catch (RuntimeException ex) {
            log.warn("Report summary cache write failed");
        }
    }

    @Override
    public void evict(UUID reportId) {
        try {
            redisTemplate.delete(key(reportId));
        } catch (RuntimeException ex) {
            log.warn("Report summary cache eviction failed");
        }
    }

    private static String key(UUID reportId) {
        return "report:" + reportId + ":summary";
    }
}
