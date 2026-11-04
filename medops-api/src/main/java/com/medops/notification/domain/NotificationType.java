package com.medops.notification.domain;

/**
 * Categories of user notifications produced from domain events.
 */
public enum NotificationType {
    APPOINTMENT_BOOKED,
    APPOINTMENT_CANCELLED,
    APPOINTMENT_RESCHEDULED,
    REPORT_UPLOADED,
    PAYMENT_CONFIRMED
}
