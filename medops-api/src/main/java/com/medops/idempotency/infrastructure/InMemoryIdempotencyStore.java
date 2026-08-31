package com.medops.idempotency.infrastructure;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.medops.idempotency.domain.IdempotencyRecord;
import com.medops.idempotency.domain.IdempotencyStore;

@Component
@ConditionalOnProperty(prefix = "medops.redis", name = "enabled", havingValue = "false")
public final class InMemoryIdempotencyStore implements IdempotencyStore {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<IdempotencyRecord> find(String storeKey) {
        Entry entry = entries.get(storeKey);
        if (entry == null || entry.expiresAtMillis < System.currentTimeMillis()) {
            entries.remove(storeKey, entry);
            return Optional.empty();
        }
        return Optional.of(entry.idempotencyRecord);
    }

    @Override
    public boolean tryBegin(String storeKey, String requestHash, Duration ttl) {
        long expiresAt = System.currentTimeMillis() + ttl.toMillis();
        Entry created = new Entry(IdempotencyRecord.started(requestHash), expiresAt);
        Entry existing = entries.putIfAbsent(storeKey, created);
        if (existing == null) {
            return true;
        }
        if (existing.expiresAtMillis < System.currentTimeMillis()) {
            return entries.replace(storeKey, existing, created);
        }
        return false;
    }

    @Override
    public void complete(String storeKey, String requestHash, String responseJson, Duration ttl) {
        long expiresAt = System.currentTimeMillis() + ttl.toMillis();
        entries.put(storeKey, new Entry(IdempotencyRecord.completed(requestHash, responseJson), expiresAt));
    }

    @Override
    public void abandon(String storeKey) {
        entries.remove(storeKey);
    }

    private record Entry(IdempotencyRecord idempotencyRecord, long expiresAtMillis) {
    }
}
