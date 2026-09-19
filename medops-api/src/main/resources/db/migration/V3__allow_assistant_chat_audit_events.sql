-- Allow the AI assistant chat flow to write audit events.
-- The V2 check constraint predates the ASSISTANT_CHAT event type, so the audit
-- insert after a successful assistant reply failed with a data-integrity
-- violation (surfaced as an HTTP 500) even though the LLM reply had already
-- been produced.
ALTER TABLE audit.audit_events DROP CONSTRAINT audit_events_event_type_check;

ALTER TABLE audit.audit_events ADD CONSTRAINT audit_events_event_type_check CHECK (
    event_type::text = ANY (ARRAY[
        'AUTH_REGISTER', 'AUTH_LOGIN_SUCCESS', 'AUTH_LOGIN_FAILURE', 'AUTH_LOGIN_LOCKED',
        'AUTH_TOKEN_REFRESH_SUCCESS', 'AUTH_TOKEN_REFRESH_FAILURE', 'AUTH_LOGOUT',
        'APPOINTMENT_BOOKED', 'APPOINTMENT_CANCELLED', 'APPOINTMENT_RESCHEDULED', 'APPOINTMENT_COMPLETED',
        'REPORT_UPLOADED', 'REPORT_REVIEWED', 'REPORT_VIEWED', 'REPORT_SUMMARIZED',
        'ASSISTANT_CHAT',
        'PRESCRIPTION_CREATED',
        'INVOICE_CREATED', 'INVOICE_ISSUED', 'INVOICE_VOIDED',
        'PAYMENT_RECORDED', 'PAYMENT_REFUNDED',
        'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_OTP_VERIFIED', 'PASSWORD_RESET_OTP_FAILED', 'PASSWORD_RESET_SUCCESS'
    ]::text[])
);
