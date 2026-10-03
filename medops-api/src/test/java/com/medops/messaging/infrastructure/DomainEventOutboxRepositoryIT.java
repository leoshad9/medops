package com.medops.messaging.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
class DomainEventOutboxRepositoryIT {

    @Autowired private DomainEventOutboxRepository repository;
    @Autowired private DomainEventOutboxOperations operations;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void claimsPendingRowsAndUpdatesLease() {
        UUID id = UUID.randomUUID();
        repository.saveAndFlush(new DomainEventOutboxEntity(
                id, "AppointmentBooked", UUID.randomUUID().toString(),
                objectMapper.createObjectNode().put("appointmentId", UUID.randomUUID().toString()),
                Instant.now()));

        var claimed = operations.claim(10);

        assertThat(claimed).extracting(DomainEventOutboxEntity::getId).contains(id);
        assertThat(claimed.stream().filter(row -> row.getId().equals(id)).findFirst().orElseThrow().getClaimedUntil())
                .isAfter(Instant.now());
    }
}
