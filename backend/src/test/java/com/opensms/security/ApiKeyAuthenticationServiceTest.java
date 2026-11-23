package com.opensms.security;

import com.opensms.apikey.entity.ApiKey;
import com.opensms.apikey.entity.ApiKeyStatus;
import com.opensms.apikey.repository.ApiKeyRepository;
import com.opensms.apikey.service.ApiKeyGenerator;
import com.opensms.tenant.entity.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationServiceTest {

    private static final String PLAINTEXT_KEY = "os_live_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
    private static final String KEY_HASH = "b".repeat(64);

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private ApiKeyGenerator apiKeyGenerator;

    private ApiKeyAuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new ApiKeyAuthenticationService(apiKeyRepository, apiKeyGenerator);
    }

    @Test
    void authenticatesActiveKeyAndReturnsTenantIdentity() {
        Tenant tenant = new Tenant("Acme");
        ApiKey apiKey = new ApiKey(tenant, "Production", "os_live_abcdefgh", KEY_HASH);
        when(apiKeyGenerator.hash(PLAINTEXT_KEY)).thenReturn(KEY_HASH);
        when(apiKeyRepository.findByKeyHashAndStatus(KEY_HASH, ApiKeyStatus.ACTIVE))
                .thenReturn(Optional.of(apiKey));

        TenantPrincipal principal = authenticationService.authenticate(PLAINTEXT_KEY);

        assertEquals(tenant.getId(), principal.tenantId());
        assertEquals(apiKey.getId(), principal.apiKeyId());
        assertEquals(tenant.getId().toString(), principal.getName());
        verify(apiKeyGenerator).hash(PLAINTEXT_KEY);
        verify(apiKeyRepository).findByKeyHashAndStatus(KEY_HASH, ApiKeyStatus.ACTIVE);
    }

    @Test
    void rejectsInvalidKeyWithoutExposingCredential() {
        when(apiKeyGenerator.hash(PLAINTEXT_KEY)).thenReturn(KEY_HASH);
        when(apiKeyRepository.findByKeyHashAndStatus(KEY_HASH, ApiKeyStatus.ACTIVE))
                .thenReturn(Optional.empty());

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authenticationService.authenticate(PLAINTEXT_KEY)
        );

        assertEquals("Invalid API key.", exception.getMessage());
        verify(apiKeyRepository).findByKeyHashAndStatus(KEY_HASH, ApiKeyStatus.ACTIVE);
    }

    @Test
    void rejectsRevokedKeyBecauseLookupRequiresActiveStatus() {
        ApiKey revokedApiKey = new ApiKey(new Tenant("Acme"), "Production", "os_live_abcdefgh", KEY_HASH);
        revokedApiKey.revoke(Instant.now());
        assertEquals(ApiKeyStatus.REVOKED, revokedApiKey.getStatus());
        when(apiKeyGenerator.hash(PLAINTEXT_KEY)).thenReturn(KEY_HASH);
        when(apiKeyRepository.findByKeyHashAndStatus(KEY_HASH, ApiKeyStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authenticationService.authenticate(PLAINTEXT_KEY));

        verify(apiKeyRepository).findByKeyHashAndStatus(KEY_HASH, ApiKeyStatus.ACTIVE);
    }

    @Test
    void rejectsMissingOrBlankCredentialBeforeHashLookup() {
        assertThrows(BadCredentialsException.class, () -> authenticationService.authenticate(null));
        assertThrows(BadCredentialsException.class, () -> authenticationService.authenticate(" \t "));

        verifyNoInteractions(apiKeyGenerator, apiKeyRepository);
    }
}

