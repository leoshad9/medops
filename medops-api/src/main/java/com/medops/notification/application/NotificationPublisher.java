package com.medops.notification.application;

import java.util.UUID;

import com.medops.notification.domain.Notification;

/**
 * Port for realtime notification delivery. The notification use-case depends on this
 * contract only; infrastructure adapters (for example {@code NotificationStreamPublisher})
 * provide the actual transport.
 */
public interface NotificationPublisher {

    void publish(UUID userId, Notification notification);
}
