package com.opensms.contact.dto;

import java.time.Instant;
import java.util.UUID;

public record ContactResponse(
        UUID id,
        String name,
        String phoneNumber,
        String email,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
