package com.medops.shared.audit;

/**
 * Categorizes {@link AuditEvent} records. Grows as new security-sensitive or clinically
 * consequential actions (PHI access, record amendments, approvals, exports, ...) are added
 * across capabilities.
 */
public enum AuditEventType {
    AUTH_REGISTER,
    AUTH_LOGIN_SUCCESS,
    AUTH_LOGIN_FAILURE,
    AUTH_LOGIN_LOCKED,
    AUTH_TOKEN_REFRESH_SUCCESS,
    AUTH_TOKEN_REFRESH_FAILURE,
    AUTH_LOGOUT,
    APPOINTMENT_BOOKED,
    APPOINTMENT_CANCELLED,
    APPOINTMENT_RESCHEDULED,
    APPOINTMENT_COMPLETED,
    REPORT_UPLOADED,
    REPORT_REVIEWED,
    REPORT_VIEWED,
    REPORT_SUMMARIZED,
    PRESCRIPTION_CREATED
}
