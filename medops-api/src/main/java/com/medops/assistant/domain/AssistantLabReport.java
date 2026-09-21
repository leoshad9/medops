package com.medops.assistant.domain;

/**
 * LLM-safe projection of one clinical report (lab result) belonging to the
 * authenticated user.
 *
 * <p>Deliberately excludes identifiers, MRNs, and free-text clinical notes:
 * the external AI service must only ever receive the minimum context needed
 * to answer report-related questions. Times are pre-formatted in the user's local
 * zone so the model never performs timezone arithmetic.
 *
 * @param createdAtLocal  creation time rendered in the user's local zone
 * @param title           report title (e.g., "CBC", "Lipid Panel")
 * @param status          report status (PENDING, REVIEWED, ...)
 * @param doctorName      ordering doctor's display name, may be null
 * @param specialty       ordering doctor's specialty, may be null
 * @param hasSummary      whether an AI summary is available
 * @param summary         AI-generated summary, may be null
 */
public record AssistantLabReport(
        String createdAtLocal,
        String title,
        String status,
        String doctorName,
        String specialty,
        boolean hasSummary,
        String summary) {
}
