CREATE TABLE webhook_subscriptions (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    url VARCHAR(2048) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    event_types JSONB NOT NULL,
    encrypted_signing_secret BYTEA NOT NULL,
    signing_secret_iv BYTEA NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_webhook_subscriptions PRIMARY KEY (id),
    CONSTRAINT fk_webhook_subscriptions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT chk_webhook_subscriptions_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT chk_webhook_subscriptions_event_types CHECK (
        CASE WHEN jsonb_typeof(event_types) = 'array' THEN
            jsonb_array_length(event_types) > 0
            AND event_types <@ '["message.queued", "message.simulated", "message.sent", "message.failed"]'::jsonb
        ELSE FALSE END
    ),
    CONSTRAINT chk_webhook_subscriptions_secret_iv_length CHECK (octet_length(signing_secret_iv) = 12)
);

CREATE INDEX idx_webhook_subscriptions_tenant_created
    ON webhook_subscriptions (tenant_id, created_at DESC);
CREATE INDEX idx_webhook_subscriptions_tenant_status
    ON webhook_subscriptions (tenant_id, status);
