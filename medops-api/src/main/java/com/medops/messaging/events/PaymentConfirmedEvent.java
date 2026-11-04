package com.medops.messaging.events;

import java.time.Instant;
import java.util.UUID;

import com.medops.messaging.domain.MedopsDomainEvent;

public record PaymentConfirmedEvent(UUID invoiceId, UUID patientProfileId, Instant occurredAt)
        implements MedopsDomainEvent {

    public static PaymentConfirmedEvent of(UUID invoiceId, UUID patientProfileId) {
        return new PaymentConfirmedEvent(invoiceId, patientProfileId, Instant.now());
    }

    @Override
    public String eventType() {
        return "PaymentConfirmed";
    }

    @Override
    public String topicKey() {
        return invoiceId.toString();
    }
}
