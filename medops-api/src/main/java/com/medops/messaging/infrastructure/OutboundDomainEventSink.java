package com.medops.messaging.infrastructure;

import com.medops.messaging.domain.MedopsDomainEvent;

/**
 * Outbound sink for after-commit domain events (Kafka or no-op).
 */
public interface OutboundDomainEventSink {

    void send(MedopsDomainEvent event);
}
