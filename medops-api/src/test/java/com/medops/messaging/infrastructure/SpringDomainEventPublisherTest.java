package com.medops.messaging.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medops.messaging.events.AppointmentBookedEvent;

@ExtendWith(MockitoExtension.class)
class SpringDomainEventPublisherTest {

    @Mock
    private DomainEventOutboxRepository outboxRepository;

    @Test
    void persistsIdentifierOnlyPayloadWithStableOutboxId() {
        ObjectMapper objectMapper = new ObjectMapper();
        SpringDomainEventPublisher publisher = new SpringDomainEventPublisher(outboxRepository, objectMapper);
        UUID appointmentId = UUID.randomUUID();
        when(outboxRepository.save(any(DomainEventOutboxEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        publisher.publishAfterCommit(new AppointmentBookedEvent(appointmentId, Instant.parse("2026-04-01T00:00:00Z")));

        var captor = org.mockito.ArgumentCaptor.forClass(DomainEventOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        DomainEventOutboxEntity row = captor.getValue();
        assertThat(row.getEventType()).isEqualTo("AppointmentBooked");
        assertThat(row.getEventKey()).isEqualTo(appointmentId.toString());
        assertThat(row.getPayload().fieldNames()).toIterable().containsExactly("appointmentId");
        assertThat(row.getPayload().get("appointmentId").asText()).isEqualTo(appointmentId.toString());
        assertThat(row.getId()).isNotNull();
    }
}
