package com.opensms.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
        @NotBlank(message = "Tenant name must not be blank.")
        @Size(max = 150, message = "Tenant name must not exceed 150 characters.")
        String name
) {
}
