package com.opensms.security;

import java.security.Principal;
import java.util.Objects;
import java.util.UUID;

public record TenantPrincipal(UUID tenantId, UUID apiKeyId) implements Principal {

    public TenantPrincipal {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(apiKeyId, "apiKeyId must not be null");
    }

    @Override
    public String getName() {
        return tenantId.toString();
    }
}
