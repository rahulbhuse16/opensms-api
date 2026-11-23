-- SIMULATED distinguishes mock gateway acceptance from real provider acceptance (SENT).
ALTER TABLE messages DROP CONSTRAINT chk_messages_status;
ALTER TABLE messages ADD CONSTRAINT chk_messages_status
    CHECK (status IN ('QUEUED', 'PROCESSING', 'SENT', 'DELIVERED', 'SIMULATED', 'FAILED'));

-- A claim lease allows abandoned PROCESSING events to become eligible after a bounded timeout.
-- The token prevents an expired worker from overwriting a newer worker's final result.
ALTER TABLE outbox_events
    ADD COLUMN claimed_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN claim_token UUID NULL;

-- Events left PROCESSING by a pre-lease deployment are made eligible for a fresh claim.
UPDATE outbox_events SET status = 'PENDING' WHERE status = 'PROCESSING';

ALTER TABLE outbox_events ADD CONSTRAINT chk_outbox_events_claim_fields
    CHECK ((status = 'PROCESSING' AND claimed_at IS NOT NULL AND claim_token IS NOT NULL)
        OR (status <> 'PROCESSING' AND claimed_at IS NULL AND claim_token IS NULL));

CREATE INDEX idx_outbox_events_status_claimed_at
    ON outbox_events (status, claimed_at);
