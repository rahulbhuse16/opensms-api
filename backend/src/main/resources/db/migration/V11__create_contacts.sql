-- Names are limited to 150 characters, E.164 numbers to 8-15 digits plus '+',
-- email addresses to 254 characters, and descriptions to 1000 characters.
CREATE TABLE contacts (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    email VARCHAR(254) NULL,
    description VARCHAR(1000) NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_contacts PRIMARY KEY (id),
    CONSTRAINT fk_contacts_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT uq_contacts_tenant_phone UNIQUE (tenant_id, phone_number),
    CONSTRAINT chk_contacts_name_not_blank CHECK (name ~ '[^[:space:]]'),
    CONSTRAINT chk_contacts_phone_e164 CHECK (
        length(phone_number) BETWEEN 9 AND 16
        AND left(phone_number, 1) = '+'
        AND substring(phone_number, 2, 1) BETWEEN '1' AND '9'
        AND translate(substring(phone_number, 2), '0123456789', '') = ''
    )
);

CREATE INDEX idx_contacts_tenant_created_id
    ON contacts (tenant_id, created_at DESC, id DESC);
CREATE INDEX idx_contacts_tenant_name_prefix
    ON contacts (tenant_id, lower(name) text_pattern_ops);
CREATE INDEX idx_contacts_tenant_phone_prefix
    ON contacts (tenant_id, phone_number text_pattern_ops);
