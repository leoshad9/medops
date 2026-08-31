package com.medops.cache.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.medops.cache.domain.ReportSummaryCache;

@Component
@ConditionalOnProperty(prefix = "medops.redis", name = "enabled", havingValue = "false")
public final class NoOpReportSummaryCache implements ReportSummaryCache {

    @Override
    public Optional<String> get(UUID reportId) {
        return Optional.empty();
    }

    @Override
    public void put(UUID reportId, String summary) {
        // no-op
    }

    @Override
    public void evict(UUID reportId) {
        // no-op
    }
}
