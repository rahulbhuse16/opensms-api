CREATE TABLE message_status_history (
    id UUID NOT NULL,
    message_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    previous_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NOT NULL,
    change_source VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_message_status_history PRIMARY KEY (id),
    CONSTRAINT fk_message_status_history_message FOREIGN KEY (message_id) REFERENCES messages (id),
    CONSTRAINT fk_message_status_history_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT chk_message_status_history_previous_status CHECK (
        previous_status IS NULL OR previous_status IN ('QUEUED', 'PROCESSING', 'SENT', 'DELIVERED', 'SIMULATED', 'FAILED')
    ),
    CONSTRAINT chk_message_status_history_new_status CHECK (
        new_status IN ('QUEUED', 'PROCESSING', 'SENT', 'DELIVERED', 'SIMULATED', 'FAILED')
    )
);

CREATE INDEX idx_message_status_history_message_created
    ON message_status_history (message_id, created_at);
CREATE INDEX idx_message_status_history_tenant_created
    ON message_status_history (tenant_id, created_at);
