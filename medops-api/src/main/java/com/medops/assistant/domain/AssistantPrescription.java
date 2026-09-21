package com.medops.assistant.domain;

/**
 * LLM-safe projection of one prescription belonging to the
 * authenticated user.
 *
 * <p>Deliberately excludes identifiers, MRNs, and free-text clinical notes:
 * the external AI service must only ever receive the minimum context needed
 * to answer prescription-related questions.
 *
 * @param createdAtLocal    creation time rendered in the user's local zone
 * @param medicationName    medication name
 * @param dosage            dosage instructions
 * @param status            prescription status (ACTIVE, COMPLETED, ...)
 * @param doctorName        prescribing doctor's display name, may be null
 * @param specialty         prescribing doctor's specialty, may be null
 * @param refillsRemaining  number of refills remaining, may be null
 */
public record AssistantPrescription(
        String createdAtLocal,
        String medicationName,
        String dosage,
        String status,
        String doctorName,
        String specialty,
        Integer refillsRemaining) {
}
