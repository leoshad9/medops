package com.medops.messaging.infrastructure;

import java.time.Instant;
import java.util.UUID;

/**
 * Compact JSON payload published to Kafka — UUIDs and metadata only.
 */
public record DomainEventMessage(
        String eventType,
        UUID appointmentId,
        UUID reportId,
        Instant occurredAt
) {

    public static DomainEventMessage appointmentBooked(UUID appointmentId, Instant occurredAt) {
        return new DomainEventMessage("AppointmentBooked", appointmentId, null, occurredAt);
    }

    public static DomainEventMessage reportUploaded(UUID reportId, Instant occurredAt) {
        return new DomainEventMessage("ReportUploaded", null, reportId, occurredAt);
    }
}
