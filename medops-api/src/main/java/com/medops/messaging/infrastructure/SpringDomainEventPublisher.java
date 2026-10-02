package com.medops.messaging.infrastructure;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.messaging.domain.DomainEventPublisher;
import com.medops.messaging.domain.MedopsDomainEvent;
import com.medops.shared.exception.ServiceUnavailableException;

import lombok.RequiredArgsConstructor;

/** Persists an event in the same transaction as its domain change for reliable later delivery. */
@Component
@RequiredArgsConstructor
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final DomainEventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void publishAfterCommit(MedopsDomainEvent event) {
        try {
            outboxRepository.save(new DomainEventOutboxEntity(
                    java.util.UUID.randomUUID(),
                    event.eventType(),
                    event.topicKey(),
                    payload(event),
                    event.occurredAt()));
        } catch (IllegalArgumentException ex) {
            throw new ServiceUnavailableException("Unable to serialize domain event", ex);
        }
    }

    private com.fasterxml.jackson.databind.JsonNode payload(MedopsDomainEvent event) {
        com.fasterxml.jackson.databind.node.ObjectNode node = objectMapper.createObjectNode();
        if (event instanceof com.medops.messaging.events.AppointmentBookedEvent booked) {
            node.put("appointmentId", booked.appointmentId().toString());
        } else if (event instanceof com.medops.messaging.events.ReportUploadedEvent uploaded) {
            node.put("reportId", uploaded.reportId().toString());
        } else {
            throw new IllegalArgumentException("Unsupported domain event: " + event.eventType());
        }
        return node;
    }
}
