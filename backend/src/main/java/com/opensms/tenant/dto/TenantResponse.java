package com.opensms.tenant.dto;

import com.opensms.tenant.entity.TenantStatus;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String name,
        TenantStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
