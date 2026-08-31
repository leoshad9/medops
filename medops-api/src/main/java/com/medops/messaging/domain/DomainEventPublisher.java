package com.medops.messaging.domain;

/**
 * Publishes domain events after the surrounding transaction commits. Payloads must stay
 * identifier-only — never include PHI.
 */
public interface DomainEventPublisher {

    void publishAfterCommit(MedopsDomainEvent event);
}
