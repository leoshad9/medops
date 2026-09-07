package com.medops.notification.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.medops.notification.domain.Notification;
import com.medops.notification.domain.NotificationType;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String message,
        String referenceType,
        UUID referenceId,
        boolean read,
        Instant createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.id(),
                notification.type(),
                notification.title(),
                notification.message(),
                notification.referenceType(),
                notification.referenceId(),
                notification.read(),
                notification.createdAt());
    }
}
