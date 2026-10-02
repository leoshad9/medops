package com.medops.messaging.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class DomainEventOutboxDispatcherTest {

    @Mock private DomainEventOutboxOperations operations;
    @Mock private KafkaTemplate<String, DomainEventMessage> kafkaTemplate;

    @Test
    void marksOutboxRowPublishedAfterKafkaAck() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        var row = new DomainEventOutboxEntity(
                eventId, "ReportUploaded", reportId.toString(),
                new ObjectMapper().readTree("{\"reportId\":\"" + reportId + "\"}"), Instant.now());
        when(operations.claim(10)).thenReturn(List.of(row));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        var properties = new MessagingProperties(true, "appointments", "reports", 10, 1000, 3, 1000);
        var dispatcher = new DomainEventOutboxDispatcher(
                operations, kafkaTemplate, properties, new SimpleMeterRegistry());

        dispatcher.dispatchPending();

        verify(operations).published(eventId);
        verify(operations, never()).failed(any(), anyString());
    }

    @Test
    void releasesClaimForRetryWhenKafkaSendFails() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        var row = new DomainEventOutboxEntity(
                eventId, "AppointmentBooked", appointmentId.toString(),
                new ObjectMapper().readTree("{\"appointmentId\":\"" + appointmentId + "\"}"), Instant.now());
        when(operations.claim(10)).thenReturn(List.of(row));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        var properties = new MessagingProperties(true, "appointments", "reports", 10, 1000, 3, 1000);
        var dispatcher = new DomainEventOutboxDispatcher(
                operations, kafkaTemplate, properties, new SimpleMeterRegistry());

        dispatcher.dispatchPending();

        org.mockito.ArgumentCaptor<String> error = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(operations).failed(org.mockito.ArgumentMatchers.eq(eventId), error.capture());
        org.assertj.core.api.Assertions.assertThat(error.getValue()).contains("broker unavailable");
    }
}
