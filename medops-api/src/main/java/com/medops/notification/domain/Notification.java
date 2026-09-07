package com.medops.notification.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A single user notification derived from an after-commit domain event.
 *
 * @param sourceEventId Kafka envelope id of the originating domain event; used for
 *                      at-least-once idempotency (may be null for pre-envelope events).
 */
public record Notification(
        UUID id,
        UUID userId,
        NotificationType type,
        String title,
        String message,
        String referenceType,
        UUID referenceId,
        boolean read,
        Instant createdAt,
        Instant readAt,
        UUID sourceEventId
) {
}
