package com.medops.notification.application;

import java.util.UUID;

import com.medops.notification.domain.NotificationType;

/**
 * Command to create one notification for one user from a consumed domain event.
 */
public record CreateNotification(
        UUID userId,
        NotificationType type,
        String title,
        String message,
        String referenceType,
        UUID referenceId,
        UUID sourceEventId
) {
}
