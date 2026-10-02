package com.medops.messaging.infrastructure;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Polls committed outbox rows and only marks them sent after Kafka acknowledges the write. */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "medops.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DomainEventOutboxDispatcher {

    private final DomainEventOutboxOperations outboxOperations;
    private final KafkaTemplate<String, DomainEventMessage> kafkaTemplate;
    private final MessagingProperties properties;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelayString = "${medops.messaging.outbox-fixed-delay-ms:1000}")
    public void dispatchPending() {
        for (DomainEventOutboxEntity row : outboxOperations.claim(properties.outboxBatchSize())) {
            try {
                DomainEventMessage message = toMessage(row);
                String topic = topicFor(row.getEventType());
                kafkaTemplate.send(topic, row.getEventKey(), message)
                        .get(10, TimeUnit.SECONDS);
                outboxOperations.published(row.getId());
                meterRegistry.counter("domain.event.outbox.published", "event_type", row.getEventType()).increment();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                outboxOperations.failed(row.getId(), message.substring(0, Math.min(message.length(), 1000)));
                log.warn("Outbox publish interrupted eventId={} error={}", row.getId(), message);
                meterRegistry.counter("domain.event.outbox.failure", "event_type", row.getEventType()).increment();
                return;
            } catch (Exception ex) {
                String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                outboxOperations.failed(row.getId(), message.substring(0, Math.min(message.length(), 1000)));
                log.warn("Outbox publish failed eventId={} error={}", row.getId(), message);
                meterRegistry.counter("domain.event.outbox.failure", "event_type", row.getEventType()).increment();
            }
        }
    }

    private DomainEventMessage toMessage(DomainEventOutboxEntity row) {
        return new DomainEventMessage(
                row.getEventType(),
                row.getId(),
                uuidField(row, "appointmentId"),
                uuidField(row, "reportId"),
                row.getOccurredAt() == null ? Instant.now() : row.getOccurredAt());
    }

    private UUID uuidField(DomainEventOutboxEntity row, String name) {
        if (row.getPayload() == null) {
            return null;
        }
        var value = row.getPayload().get(name);
        return value == null || value.isNull() ? null : UUID.fromString(value.asText());
    }

    private String topicFor(String eventType) {
        return switch (eventType) {
            case "AppointmentBooked" -> properties.appointmentsTopic();
            case "ReportUploaded" -> properties.reportsTopic();
            default -> throw new IllegalArgumentException("Unsupported outbox event type: " + eventType);
        };
    }
}
