package com.medops.cache.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Caches plain-language report summaries by report id. Never stores PDF bytes or MRNs.
 */
public interface ReportSummaryCache {

    Optional<String> get(UUID reportId);

    void put(UUID reportId, String summary);

    void evict(UUID reportId);
}
