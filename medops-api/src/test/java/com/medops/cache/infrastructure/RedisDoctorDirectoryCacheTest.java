package com.medops.cache.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.doctors.api.dto.DoctorSummaryResponse;
import com.medops.reports.infrastructure.AiClientProperties;

@ExtendWith(MockitoExtension.class)
class RedisDoctorDirectoryCacheTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> values;

    @Test
    void evictAllAdvancesGenerationInsteadOfScanningKeys() {
        var cache = new RedisDoctorDirectoryCache(
                redisTemplate, new ObjectMapper(),
                new AiClientProperties(false, "http://localhost", Duration.ofHours(1), Duration.ofMinutes(5)));

        cache.evictAll();

        verify(redisTemplate).execute(
                org.mockito.ArgumentMatchers.eq(RedisRateLimitScripts.ADVANCE_GENERATION),
                org.mockito.ArgumentMatchers.eq(List.of("doctors:list:generation")));
        verify(redisTemplate, never()).keys(any());
    }

    @Test
    void fillWrittenIntoOldGenerationIsRemovedAfterConcurrentEviction() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("doctors:list:generation")).thenReturn("4", "5");
        var cache = new RedisDoctorDirectoryCache(
                redisTemplate, new ObjectMapper(),
                new AiClientProperties(false, "http://localhost", Duration.ofHours(1), Duration.ofMinutes(5)));
        var doctor = new DoctorSummaryResponse(
                java.util.UUID.randomUUID(), "Dr Test", "Cardiology", "test@example.org");

        cache.put("cardiology", List.of(doctor));

        verify(values).set(org.mockito.ArgumentMatchers.eq("doctors:list:4:cardiology"),
                any(String.class), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(5)));
        verify(redisTemplate).delete("doctors:list:4:cardiology");
    }
}
