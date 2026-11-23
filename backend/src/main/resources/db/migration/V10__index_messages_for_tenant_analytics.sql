-- The composite covering index supports tenant/date analytics and replaces the tenant-only index.
DROP INDEX idx_messages_tenant_id;

CREATE INDEX idx_messages_tenant_created_at
    ON messages (tenant_id, created_at) INCLUDE (status);
