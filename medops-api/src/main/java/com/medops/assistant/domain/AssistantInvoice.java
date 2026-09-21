package com.medops.assistant.domain;

/**
 * LLM-safe projection of one invoice (billing) belonging to the
 * authenticated user.
 *
 * <p>Deliberately excludes identifiers and detailed line items:
 * the external AI service must only ever receive the minimum context needed
 * to answer billing-related questions. Times are pre-formatted in the user's local
 * zone so the model never performs timezone arithmetic.
 *
 * @param createdAtLocal   creation time rendered in the user's local zone
 * @param status           invoice status (DRAFT, ISSUED, PAID, VOID)
 * @param totalCents       total amount in cents
 * @param paidCents        amount paid in cents
 * @param balanceCents     remaining balance in cents
 * @param dueDateLocal     due date rendered in the user's local zone, may be null
 * @param appointmentType  type of appointment related to invoice, may be null
 */
public record AssistantInvoice(
        String createdAtLocal,
        String status,
        long totalCents,
        long paidCents,
        long balanceCents,
        String dueDateLocal,
        String appointmentType) {
}
