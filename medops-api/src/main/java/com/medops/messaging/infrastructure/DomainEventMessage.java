package com.medops.messaging.infrastructure;

import java.time.Instant;
import java.util.UUID;

/**
 * Compact JSON payload published to Kafka — UUIDs and metadata only.
 *
 * <p>{@code eventId} uniquely identifies this published message. Consumers use it for
 * idempotent processing (at-least-once delivery + a unique constraint on the consumed
 * side = exactly-once effect).
 */
public record DomainEventMessage(
        String eventType,
        UUID eventId,
        UUID appointmentId,
        UUID reportId,
        UUID invoiceId,
        UUID patientProfileId,
        Instant occurredAt
) {

    public static DomainEventMessage appointmentBooked(UUID appointmentId, Instant occurredAt) {
        return new DomainEventMessage(
                "AppointmentBooked", UUID.randomUUID(), appointmentId, null, null, null, occurredAt);
    }

    public static DomainEventMessage reportUploaded(UUID reportId, Instant occurredAt) {
        return new DomainEventMessage(
                "ReportUploaded", UUID.randomUUID(), null, reportId, null, null, occurredAt);
    }

    public static DomainEventMessage appointmentCancelled(UUID appointmentId, Instant occurredAt) {
        return new DomainEventMessage(
                "AppointmentCancelled", UUID.randomUUID(), appointmentId, null, null, null, occurredAt);
    }

    public static DomainEventMessage appointmentRescheduled(UUID appointmentId, Instant occurredAt) {
        return new DomainEventMessage(
                "AppointmentRescheduled", UUID.randomUUID(), appointmentId, null, null, null, occurredAt);
    }

    public static DomainEventMessage paymentConfirmed(UUID invoiceId, UUID patientProfileId, Instant occurredAt) {
        return new DomainEventMessage(
                "PaymentConfirmed", UUID.randomUUID(), null, null, invoiceId, patientProfileId, occurredAt);
    }
}

