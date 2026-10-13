-- Stripe integration: update payments table for Stripe method and add audit event types.

-- 1. Add STRIPE to the payments method check constraint.
ALTER TABLE billing.payments
    DROP CONSTRAINT payments_method_check;

ALTER TABLE billing.payments
    ADD CONSTRAINT payments_method_check
        CHECK (method IN ('CASH', 'CARD', 'INSURANCE', 'BANK_TRANSFER', 'STRIPE'));

-- 2. Add new audit event types for Stripe checkout session creation and payment verification.
ALTER TABLE audit.audit_events
    DROP CONSTRAINT audit_events_event_type_check;

ALTER TABLE audit.audit_events
    ADD CONSTRAINT audit_events_event_type_check
        CHECK (event_type IN (
            'AUTH_REGISTER', 'AUTH_LOGIN_SUCCESS', 'AUTH_LOGIN_FAILURE',
            'AUTH_LOGIN_LOCKED', 'AUTH_TOKEN_REFRESH_SUCCESS', 'AUTH_TOKEN_REFRESH_FAILURE',
            'AUTH_LOGOUT', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_OTP_VERIFIED',
            'PASSWORD_RESET_OTP_FAILED', 'PASSWORD_RESET_SUCCESS',
            'APPOINTMENT_BOOKED', 'APPOINTMENT_CANCELLED', 'APPOINTMENT_RESCHEDULED',
            'APPOINTMENT_COMPLETED', 'REPORT_UPLOADED', 'REPORT_REVIEWED', 'REPORT_VIEWED',
            'REPORT_SUMMARIZED', 'ASSISTANT_CHAT', 'PRESCRIPTION_CREATED',
            'INVOICE_CREATED', 'INVOICE_ISSUED', 'INVOICE_VOIDED',
            'PAYMENT_RECORDED', 'PAYMENT_REFUNDED',
            'STRIPE_CHECKOUT_SESSION_CREATED', 'STRIPE_PAYMENT_SUCCEEDED', 'STRIPE_WEBHOOK_RECEIVED'
        ));

-- 3. Index the webhook dedup lookup: handleWebhook queries findByReference
-- on every Stripe event, and reference has no index today.
CREATE INDEX IF NOT EXISTS idx_payments_reference ON billing.payments (reference);
