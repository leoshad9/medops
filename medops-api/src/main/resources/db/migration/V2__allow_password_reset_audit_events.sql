-- Allow the password-reset flow to write audit events.
-- The V1 check constraint predates the PASSWORD_RESET_* event types, so the audit
-- insert on a forgot-password request failed with a data-integrity violation
-- (surfaced as HTTP 409) even though the OTP email had already been sent.
ALTER TABLE audit.audit_events DROP CONSTRAINT audit_events_event_type_check;

ALTER TABLE audit.audit_events ADD CONSTRAINT audit_events_event_type_check CHECK (
    event_type::text = ANY (ARRAY[
        'AUTH_REGISTER', 'AUTH_LOGIN_SUCCESS', 'AUTH_LOGIN_FAILURE', 'AUTH_LOGIN_LOCKED',
        'AUTH_TOKEN_REFRESH_SUCCESS', 'AUTH_TOKEN_REFRESH_FAILURE', 'AUTH_LOGOUT',
        'APPOINTMENT_BOOKED', 'APPOINTMENT_CANCELLED', 'APPOINTMENT_RESCHEDULED', 'APPOINTMENT_COMPLETED',
        'REPORT_UPLOADED', 'REPORT_REVIEWED', 'REPORT_VIEWED', 'REPORT_SUMMARIZED',
        'PRESCRIPTION_CREATED',
        'INVOICE_CREATED', 'INVOICE_ISSUED', 'INVOICE_VOIDED',
        'PAYMENT_RECORDED', 'PAYMENT_REFUNDED',
        'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_OTP_VERIFIED', 'PASSWORD_RESET_OTP_FAILED', 'PASSWORD_RESET_SUCCESS'
    ]::text[])
);

