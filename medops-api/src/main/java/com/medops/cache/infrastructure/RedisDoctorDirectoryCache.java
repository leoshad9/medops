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

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AiClientProperties aiProperties;

    @Override
    public Optional<List<DoctorSummaryResponse>> get(String specialtyKey) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + specialtyKey);
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
            redisTemplate.opsForValue().set(KEY_PREFIX + specialtyKey, json, ttl);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("Doctor directory cache write failed");
        }
    }

    @Override
    public void evictAll() {
        try {
            var keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (RuntimeException ex) {
            log.warn("Doctor directory cache eviction failed");
        }
    }
}
