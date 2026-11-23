CREATE TABLE webhook_deliveries (
    id UUID NOT NULL,
    event_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    message_id UUID NOT NULL,
    webhook_subscription_id UUID NULL,
    subscription_id_snapshot UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    event_payload JSONB NOT NULL,
    destination_url_snapshot VARCHAR(2048) NOT NULL,
    encrypted_signing_secret_snapshot BYTEA NOT NULL,
    signing_secret_iv_snapshot BYTEA NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_webhook_deliveries PRIMARY KEY (id),
    CONSTRAINT fk_webhook_deliveries_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_webhook_deliveries_message FOREIGN KEY (message_id) REFERENCES messages (id),
    CONSTRAINT fk_webhook_deliveries_subscription FOREIGN KEY (webhook_subscription_id)
        REFERENCES webhook_subscriptions (id) ON DELETE SET NULL,
    CONSTRAINT uq_webhook_deliveries_event_subscription UNIQUE (event_id, subscription_id_snapshot),
    CONSTRAINT chk_webhook_deliveries_event_type CHECK (
        event_type IN ('message.queued', 'message.simulated', 'message.sent', 'message.failed')
    ),
    CONSTRAINT chk_webhook_deliveries_status CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_webhook_deliveries_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT chk_webhook_deliveries_secret_iv_length CHECK (octet_length(signing_secret_iv_snapshot) = 12),
    CONSTRAINT chk_webhook_deliveries_payload_object CHECK (jsonb_typeof(event_payload) = 'object')
);

CREATE INDEX idx_webhook_deliveries_tenant_pending
    ON webhook_deliveries (tenant_id, status, created_at);
CREATE INDEX idx_webhook_deliveries_subscription_history
    ON webhook_deliveries (subscription_id_snapshot, created_at DESC);
