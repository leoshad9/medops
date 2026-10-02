package com.medops.cache.infrastructure;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.cache.domain.DoctorDirectoryCache;
import com.medops.doctors.api.dto.DoctorSummaryResponse;
import com.medops.reports.infrastructure.AiClientProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "medops.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public final class RedisDoctorDirectoryCache implements DoctorDirectoryCache {

    private static final String KEY_PREFIX = "doctors:list:";
    private static final String GENERATION_KEY = "doctors:list:generation";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AiClientProperties aiProperties;

    @Override
    public Optional<List<DoctorSummaryResponse>> get(String specialtyKey) {
        try {
            String generation = currentGeneration();
            String json = redisTemplate.opsForValue().get(key(generation, specialtyKey));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, new TypeReference<>() { }));
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("Doctor directory cache read failed; falling back to database");
            return Optional.empty();
        }
    }

    @Override
    public void put(String specialtyKey, List<DoctorSummaryResponse> doctors) {
        try {
            String json = objectMapper.writeValueAsString(doctors);
            Duration ttl = aiProperties.doctorListCacheTtl();
            String generation = currentGeneration();
            String cacheKey = key(generation, specialtyKey);
            redisTemplate.opsForValue().set(cacheKey, json, ttl);
            if (!generation.equals(currentGeneration())) {
                redisTemplate.delete(cacheKey);
            }
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("Doctor directory cache write failed");
        }
    }

    @Override
    public void evictAll() {
        try {
            redisTemplate.execute(RedisRateLimitScripts.ADVANCE_GENERATION, java.util.List.of(GENERATION_KEY));
        } catch (RuntimeException ex) {
            log.warn("Doctor directory cache eviction failed");
        }
    }

    private static String key(String generation, String specialtyKey) {
        return KEY_PREFIX + (generation == null ? "0" : generation) + ':' + specialtyKey;
    }

    private String currentGeneration() {
        String generation = redisTemplate.opsForValue().get(GENERATION_KEY);
        if (generation != null) {
            return generation;
        }
        Boolean created = redisTemplate.opsForValue().setIfAbsent(GENERATION_KEY, "0");
        if (Boolean.TRUE.equals(created)) {
            return "0";
        }
        generation = redisTemplate.opsForValue().get(GENERATION_KEY);
        return generation == null ? "0" : generation;
    }
}
