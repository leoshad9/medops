package com.medops.messaging.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** Short transaction boundaries around claiming and acknowledging outbox rows. */
@Service
@RequiredArgsConstructor
public class DomainEventOutboxOperations {

    private final DomainEventOutboxRepository repository;
    private static final int DEFAULT_CLAIM_SECONDS = 30;

    @Transactional
    public List<DomainEventOutboxEntity> claim(int batchSize) {
        List<DomainEventOutboxEntity> rows = repository.claimNextBatch(batchSize);
        for (DomainEventOutboxEntity row : rows) {
            row.claim(java.time.Instant.now().plusSeconds(DEFAULT_CLAIM_SECONDS));
        }
        return rows;
    }

    @Transactional
    public void published(UUID id) {
        repository.markPublished(id);
    }

    @Transactional
    public void failed(UUID id, String error) {
        repository.recordFailure(id, error);
    }
}
