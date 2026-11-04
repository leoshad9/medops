-- Widen idempotency_key to accommodate Stripe payment intent IDs > 64 chars.
ALTER TABLE billing.payments
    ALTER COLUMN idempotency_key TYPE VARCHAR(128);
