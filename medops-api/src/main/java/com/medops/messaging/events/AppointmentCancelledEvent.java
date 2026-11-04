package com.medops.messaging.events;

import java.time.Instant;
import java.util.UUID;

import com.medops.messaging.domain.MedopsDomainEvent;

public record AppointmentCancelledEvent(UUID appointmentId, Instant occurredAt) implements MedopsDomainEvent {

    public static AppointmentCancelledEvent of(UUID appointmentId) {
        return new AppointmentCancelledEvent(appointmentId, Instant.now());
    }

    @Override
    public String eventType() {
        return "AppointmentCancelled";
    }

    @Override
    public String topicKey() {
        return appointmentId.toString();
    }
}
