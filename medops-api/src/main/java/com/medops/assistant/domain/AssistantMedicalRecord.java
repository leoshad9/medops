package com.medops.assistant.domain;

/**
 * LLM-safe projection of one medical record (visit summary, clinical note) belonging to the
 * authenticated user.
 *
 * <p>Deliberately excludes identifiers, MRNs, and free-text clinical notes:
 * the external AI service must only ever receive the minimum context needed
 * to answer medical record-related questions. Times are pre-formatted in the user's local
 * zone so the model never performs timezone arithmetic.
 *
 * @param createdAtLocal    creation time rendered in the user's local zone
 * @param title             record title (e.g., "Visit Summary", "Clinical Note")
 * @param type              record type (VISIT_SUMMARY, CLINICAL_NOTE, ...)
 * @param doctorName        authoring doctor's display name, may be null
 * @param specialty         authoring doctor's specialty, may be null
 * @param hasSummary        whether an AI summary is available
 * @param summary           AI-generated summary, may be null
 */
public record AssistantMedicalRecord(
        String createdAtLocal,
        String title,
        String type,
        String doctorName,
        String specialty,
        boolean hasSummary,
        String summary) {
}
