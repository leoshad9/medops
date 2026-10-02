package com.medops.messaging.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface DomainEventOutboxRepository extends JpaRepository<DomainEventOutboxEntity, UUID> {

    @Transactional
    @Query(value = """
            SELECT * FROM messaging.domain_event_outbox
            WHERE published_at IS NULL AND (claimed_until IS NULL OR claimed_until < now())
            ORDER BY created_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<DomainEventOutboxEntity> claimNextBatch(@Param("batchSize") int batchSize);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE messaging.domain_event_outbox
            SET published_at = now(), claimed_until = NULL, last_error = NULL
            WHERE id = :id
            """, nativeQuery = true)
    int markPublished(@Param("id") UUID id);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE messaging.domain_event_outbox
            SET attempts = attempts + 1, last_error = :error, claimed_until = NULL
            WHERE id = :id
            """, nativeQuery = true)
    int recordFailure(@Param("id") UUID id, @Param("error") String error);

}
