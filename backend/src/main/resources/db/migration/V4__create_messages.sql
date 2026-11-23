-- Destination numbers use VARCHAR(20) to accommodate E.164 numbers plus formatting headroom.
-- Message bodies use TEXT to support long and Unicode SMS content without an arbitrary database cap.
-- Status values cover queuing, asynchronous processing, and delivery outcome tracking.
CREATE TABLE messages (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    destination_phone_number VARCHAR(20) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_messages PRIMARY KEY (id),
    CONSTRAINT fk_messages_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT chk_messages_status CHECK (status IN ('QUEUED', 'PROCESSING', 'SENT', 'DELIVERED', 'FAILED'))
);

CREATE INDEX idx_messages_tenant_id ON messages (tenant_id);
CREATE INDEX idx_messages_status ON messages (status);
