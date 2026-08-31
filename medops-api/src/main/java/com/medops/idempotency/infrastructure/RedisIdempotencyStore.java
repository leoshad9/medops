package com.medops.idempotency.infrastructure;

import java.time.Duration;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.idempotency.domain.IdempotencyRecord;
import com.medops.idempotency.domain.IdempotencyStore;
import com.medops.shared.exception.ServiceUnavailableException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "medops.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public final class RedisIdempotencyStore implements IdempotencyStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<IdempotencyRecord> find(String storeKey) {
        try {
            String json = redisTemplate.opsForValue().get(redisKey(storeKey));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, IdempotencyRecord.class));
        } catch (RuntimeException | JsonProcessingException ex) {
            log.error("Idempotency store read failed");
            throw new ServiceUnavailableException("Idempotency store temporarily unavailable", ex);
        }
    }

    @Override
    public boolean tryBegin(String storeKey, String requestHash, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(IdempotencyRecord.started(requestHash));
            Boolean created = redisTemplate.opsForValue().setIfAbsent(redisKey(storeKey), json, ttl);
            return Boolean.TRUE.equals(created);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.error("Idempotency store begin failed");
            throw new ServiceUnavailableException("Idempotency store temporarily unavailable", ex);
        }
    }

    @Override
    public void complete(String storeKey, String requestHash, String responseJson, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(IdempotencyRecord.completed(requestHash, responseJson));
            redisTemplate.opsForValue().set(redisKey(storeKey), json, ttl);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.error("Idempotency store complete failed");
            throw new ServiceUnavailableException("Idempotency store temporarily unavailable", ex);
        }
    }

    @Override
    public void abandon(String storeKey) {
        try {
            redisTemplate.delete(redisKey(storeKey));
        } catch (RuntimeException ex) {
            log.error("Idempotency store abandon failed");
            throw new ServiceUnavailableException("Idempotency store temporarily unavailable", ex);
        }
    }

    private static String redisKey(String storeKey) {
        return "idempotency:" + storeKey;
    }
}
