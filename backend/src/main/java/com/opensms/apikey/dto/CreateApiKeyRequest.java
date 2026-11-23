package com.opensms.apikey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApiKeyRequest(
        @NotBlank(message = "API key name must not be blank.")
        @Size(max = 100, message = "API key name must not exceed 100 characters.")
        String name
) {
}
