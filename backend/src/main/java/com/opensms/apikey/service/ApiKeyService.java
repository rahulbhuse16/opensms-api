package com.opensms.apikey.service;

import com.opensms.apikey.dto.ApiKeyResponse;
import com.opensms.apikey.dto.CreatedApiKeyResponse;
import com.opensms.apikey.entity.ApiKey;
import com.opensms.apikey.repository.ApiKeyRepository;
import com.opensms.security.TenantPrincipal;
import com.opensms.tenant.entity.Tenant;
import com.opensms.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ApiKeyService {

    private static final int MAX_NAME_LENGTH = 100;

    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;
    private final ApiKeyGenerator apiKeyGenerator;

    public ApiKeyService(
            ApiKeyRepository apiKeyRepository,
            TenantRepository tenantRepository,
            ApiKeyGenerator apiKeyGenerator
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.tenantRepository = tenantRepository;
        this.apiKeyGenerator = apiKeyGenerator;
    }

    @Transactional
    public CreatedApiKeyResponse createApiKey(TenantPrincipal principal, String name) {
        UUID tenantId = authenticatedTenantId(principal);
        String normalizedName = normalizeName(name);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("Tenant not found: " + tenantId));
        ApiKeyGenerator.GeneratedApiKey generatedKey = apiKeyGenerator.generate();
        ApiKey apiKey = new ApiKey(tenant, normalizedName, generatedKey.prefix(), generatedKey.hash());
        ApiKey savedApiKey = apiKeyRepository.save(apiKey);

        return new CreatedApiKeyResponse(
                savedApiKey.getId(),
                savedApiKey.getName(),
                savedApiKey.getKeyPrefix(),
                generatedKey.plaintext(),
                savedApiKey.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> getApiKeys(TenantPrincipal principal) {
        UUID tenantId = authenticatedTenantId(principal);
        return apiKeyRepository.findAllByTenant_Id(tenantId).stream()
                .map(ApiKeyService::toResponse)
                .toList();
    }

    @Transactional
    public ApiKeyResponse revokeApiKey(TenantPrincipal principal, UUID apiKeyId) {
        UUID tenantId = authenticatedTenantId(principal);
        if (apiKeyId == null) {
            throw new IllegalArgumentException("API key ID must not be null.");
        }

        ApiKey apiKey = apiKeyRepository.findByIdAndTenant_Id(apiKeyId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("API key not found for tenant: " + apiKeyId));
        apiKey.revoke(Instant.now());
        return toResponse(apiKeyRepository.save(apiKey));
    }

    private String normalizeName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("API key name must not be null.");
        }
        String normalizedName = name.strip();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("API key name must not be blank.");
        }
        if (normalizedName.codePointCount(0, normalizedName.length()) > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("API key name must not exceed 100 characters.");
        }
        return normalizedName;
    }

    private UUID authenticatedTenantId(TenantPrincipal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("Authenticated tenant principal must not be null.");
        }
        return principal.tenantId();
    }

    private static ApiKeyResponse toResponse(ApiKey apiKey) {
        return new ApiKeyResponse(
                apiKey.getId(),
                apiKey.getName(),
                apiKey.getKeyPrefix(),
                apiKey.getStatus(),
                apiKey.getCreatedAt(),
                apiKey.getRevokedAt()
        );
    }
}
