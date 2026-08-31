package com.medops.messaging.events;

import java.time.Instant;
import java.util.UUID;

import com.medops.messaging.domain.MedopsDomainEvent;

public record ReportUploadedEvent(UUID reportId, Instant occurredAt) implements MedopsDomainEvent {

    public static ReportUploadedEvent of(UUID reportId) {
        return new ReportUploadedEvent(reportId, Instant.now());
    }

    @Override
    public String eventType() {
        return "ReportUploaded";
    }

    @Override
    public String topicKey() {
        return reportId.toString();
    }
}
