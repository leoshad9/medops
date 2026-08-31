package com.medops.messaging.domain;

import java.time.Instant;

/**
 * Marker for after-commit domain events. Concrete events carry UUIDs only.
 */
public interface MedopsDomainEvent {

    String eventType();

    Instant occurredAt();

    String topicKey();
}
