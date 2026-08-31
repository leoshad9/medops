package com.medops.cache.domain;

import java.util.List;
import java.util.Optional;

import com.medops.doctors.api.dto.DoctorSummaryResponse;

/**
 * Short-TTL cache for the public doctor directory. Values must not include PHI.
 */
public interface DoctorDirectoryCache {

    Optional<List<DoctorSummaryResponse>> get(String specialtyKey);

    void put(String specialtyKey, List<DoctorSummaryResponse> doctors);

    void evictAll();
}
