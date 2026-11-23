package com.opensms.apikey.dto;

import com.opensms.apikey.entity.ApiKeyStatus;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyResponse(
        UUID id,
        String name,
        String keyPrefix,
        ApiKeyStatus status,
        Instant createdAt,
        Instant revokedAt
) {
}
