package com.opensms.security;

import com.opensms.apikey.entity.ApiKey;
import com.opensms.apikey.entity.ApiKeyStatus;
import com.opensms.apikey.repository.ApiKeyRepository;
import com.opensms.apikey.service.ApiKeyGenerator;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ApiKeyAuthenticationService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid API key.";

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyGenerator apiKeyGenerator;

    public ApiKeyAuthenticationService(ApiKeyRepository apiKeyRepository, ApiKeyGenerator apiKeyGenerator) {
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyGenerator = apiKeyGenerator;
    }

    @Transactional(readOnly = true)
    public TenantPrincipal authenticate(String plaintextApiKey) {
        if (plaintextApiKey == null || plaintextApiKey.isBlank()) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        String keyHash = apiKeyGenerator.hash(plaintextApiKey);
        ApiKey apiKey = apiKeyRepository.findByKeyHashAndStatus(keyHash, ApiKeyStatus.ACTIVE)
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE));
        UUID tenantId = apiKey.getTenant().getId();
        return new TenantPrincipal(tenantId, apiKey.getId());
    }
}
