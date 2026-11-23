CREATE TABLE message_idempotency_keys (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    message_id UUID NOT NULL,
    response_created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    response_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_message_idempotency_keys PRIMARY KEY (id),
    CONSTRAINT fk_message_idempotency_keys_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_message_idempotency_keys_message FOREIGN KEY (message_id) REFERENCES messages (id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uq_message_idempotency_keys_tenant_key UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT chk_message_idempotency_keys_key CHECK (
        length(idempotency_key) BETWEEN 1 AND 128
        AND idempotency_key !~ '[[:space:][:cntrl:]]'
    ),
    CONSTRAINT chk_message_idempotency_keys_fingerprint CHECK (request_fingerprint ~ '^[0-9a-f]{64}$')
);
