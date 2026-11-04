package com.medops.messaging.events;

import java.time.Instant;
import java.util.UUID;

import com.medops.messaging.domain.MedopsDomainEvent;

public record AppointmentRescheduledEvent(UUID appointmentId, Instant occurredAt) implements MedopsDomainEvent {

    public static AppointmentRescheduledEvent of(UUID appointmentId) {
        return new AppointmentRescheduledEvent(appointmentId, Instant.now());
    }

    @Override
    public String eventType() {
        return "AppointmentRescheduled";
    }

    @Override
    public String topicKey() {
        return appointmentId.toString();
    }
}
