package com.medops.messaging.infrastructure;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "domain_event_outbox", schema = "messaging")
@Getter
@NoArgsConstructor
public class DomainEventOutboxEntity {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_key", nullable = false)
    private String eventKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "claimed_until")
    private Instant claimedUntil;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public DomainEventOutboxEntity(
            UUID id, String eventType, String eventKey, JsonNode payload, Instant occurredAt) {
        this.id = id;
        this.eventType = eventType;
        this.eventKey = eventKey;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.createdAt = Instant.now();
    }

    public void markPublished(Instant publishedAtInstant) {
        this.publishedAt = publishedAtInstant;
        this.claimedUntil = null;
        this.lastError = null;
    }

    public void claim(Instant claimedUntilInstant) {
        this.claimedUntil = claimedUntilInstant;
    }

    public void recordFailure(String error) {
        attempts++;
        this.lastError = error == null ? "Unknown Kafka publish failure"
                : error.substring(0, Math.min(error.length(), 1000));
    }
}
