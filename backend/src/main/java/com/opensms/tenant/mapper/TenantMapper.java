package com.opensms.tenant.mapper;

import com.opensms.tenant.dto.TenantResponse;
import com.opensms.tenant.entity.Tenant;
import org.springframework.stereotype.Component;

@Component
public class TenantMapper {

    public TenantResponse toResponse(Tenant tenant) {
        if (tenant == null) {
            throw new IllegalArgumentException("Tenant must not be null.");
        }

        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getStatus(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}
