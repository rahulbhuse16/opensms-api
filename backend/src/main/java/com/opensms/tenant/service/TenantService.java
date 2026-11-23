package com.opensms.tenant.service;

import com.opensms.tenant.dto.TenantResponse;
import com.opensms.tenant.entity.Tenant;
import com.opensms.tenant.entity.TenantStatus;
import com.opensms.tenant.mapper.TenantMapper;
import com.opensms.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class TenantService {

    private static final int MAX_NAME_LENGTH = 150;

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;

    public TenantService(TenantRepository tenantRepository, TenantMapper tenantMapper) {
        this.tenantRepository = tenantRepository;
        this.tenantMapper = tenantMapper;
    }

    public TenantResponse createTenant(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Tenant name must not be null.");
        }

        String normalizedName = name.strip();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("Tenant name must not be blank.");
        }
        if (normalizedName.codePointCount(0, normalizedName.length()) > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Tenant name must not exceed 150 characters.");
        }

        Tenant tenant = new Tenant(normalizedName);
        tenant.changeStatus(TenantStatus.ACTIVE);
        return tenantMapper.toResponse(tenantRepository.save(tenant));
    }

    public TenantResponse getTenantById(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Tenant ID must not be null.");
        }
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Tenant not found: " + id));
        return tenantMapper.toResponse(tenant);
    }

    public List<TenantResponse> getAllTenants() {
        return tenantRepository.findAll().stream()
                .map(tenantMapper::toResponse)
                .toList();
    }

    public boolean tenantExists(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Tenant ID must not be null.");
        }
        return tenantRepository.existsById(id);
    }
}
