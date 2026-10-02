CREATE SCHEMA IF NOT EXISTS messaging;

CREATE TABLE messaging.domain_event_outbox (
    id uuid PRIMARY KEY,
    event_type varchar(100) NOT NULL,
    event_key varchar(255) NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz,
    claimed_until timestamptz,
    attempts integer NOT NULL DEFAULT 0,
    last_error varchar(1000)
);

CREATE INDEX domain_event_outbox_pending_idx
    ON messaging.domain_event_outbox (created_at, id)
    WHERE published_at IS NULL;
