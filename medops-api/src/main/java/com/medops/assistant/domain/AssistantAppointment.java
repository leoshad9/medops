package com.medops.assistant.domain;

/**
 * LLM-safe projection of one upcoming appointment belonging to the
 * authenticated user.
 *
 * <p>Deliberately excludes identifiers, MRNs, and free-text visit reasons:
 * the external AI service must only ever receive the minimum context needed
 * to answer scheduling questions. Times are pre-formatted in the user's local
 * zone so the model never performs timezone arithmetic.
 *
 * @param startsAtLocal    start time rendered in the user's local zone
 * @param status           appointment status (BOOKED, CANCELLED, ...)
 * @param practitionerName display name of the doctor, may be null
 * @param specialty        practitioner specialty, may be null
 * @param location         clinic/location text, may be null
 */
public record AssistantAppointment(
        String startsAtLocal,
        String status,
        String practitionerName,
        String specialty,
        String location) {
}
