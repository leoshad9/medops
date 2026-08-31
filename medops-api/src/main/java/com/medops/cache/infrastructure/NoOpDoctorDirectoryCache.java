package com.medops.cache.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.medops.cache.domain.DoctorDirectoryCache;
import com.medops.doctors.api.dto.DoctorSummaryResponse;

@Component
@ConditionalOnProperty(prefix = "medops.redis", name = "enabled", havingValue = "false")
public final class NoOpDoctorDirectoryCache implements DoctorDirectoryCache {

    @Override
    public Optional<List<DoctorSummaryResponse>> get(String specialtyKey) {
        return Optional.empty();
    }

    @Override
    public void put(String specialtyKey, List<DoctorSummaryResponse> doctors) {
        // no-op
    }

    @Override
    public void evictAll() {
        // no-op
    }
}
