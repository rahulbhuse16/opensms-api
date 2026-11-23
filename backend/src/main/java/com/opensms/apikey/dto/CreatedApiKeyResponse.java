package com.opensms.apikey.dto;

import java.time.Instant;
import java.util.UUID;

public record CreatedApiKeyResponse(
        UUID id,
        String name,
        String keyPrefix,
        String apiKey,
        Instant createdAt
) {

    @Override
    public String toString() {
        return "CreatedApiKeyResponse[id=" + id
                + ", name=" + name
                + ", keyPrefix=" + keyPrefix
                + ", apiKey=<redacted>"
                + ", createdAt=" + createdAt + "]";
    }
}
