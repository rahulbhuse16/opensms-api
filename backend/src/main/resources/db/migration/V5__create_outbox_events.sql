-- JSONB keeps event payloads queryable while message content remains in the messages table.
-- Payloads should contain identifiers only; current message events use messageId and tenantId.
-- last_error uses TEXT to preserve database/provider error detail without imposing a truncation limit.
-- The composite index supports dispatcher polling by status and then earliest availability/creation time.
CREATE TABLE outbox_events (
    id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE NULL,
    last_error TEXT NULL,
    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT chk_outbox_events_status CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT chk_outbox_events_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_outbox_events_status_available_created
    ON outbox_events (status, available_at, created_at);
