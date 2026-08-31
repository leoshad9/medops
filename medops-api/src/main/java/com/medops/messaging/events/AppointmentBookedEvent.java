package com.medops.messaging.events;

import java.time.Instant;
import java.util.UUID;

import com.medops.messaging.domain.MedopsDomainEvent;

public record AppointmentBookedEvent(UUID appointmentId, Instant occurredAt) implements MedopsDomainEvent {

    public static AppointmentBookedEvent of(UUID appointmentId) {
        return new AppointmentBookedEvent(appointmentId, Instant.now());
    }

    @Override
    public String eventType() {
        return "AppointmentBooked";
    }

    @Override
    public String topicKey() {
        return appointmentId.toString();
    }
}
