package com.opensms.apikey.service;

import com.opensms.apikey.dto.ApiKeyResponse;
import com.opensms.apikey.dto.CreatedApiKeyResponse;
import com.opensms.apikey.entity.ApiKey;
import com.opensms.apikey.entity.ApiKeyStatus;
import com.opensms.apikey.repository.ApiKeyRepository;
import com.opensms.security.TenantPrincipal;
import com.opensms.tenant.entity.Tenant;
import com.opensms.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    private static final String PLAINTEXT_KEY = "os_live_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
    private static final String KEY_PREFIX = "os_live_abcdefgh";
    private static final String KEY_HASH = "a".repeat(64);

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ApiKeyGenerator apiKeyGenerator;

    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(apiKeyRepository, tenantRepository, apiKeyGenerator);
    }

    @Test
    void createsKeyReturnsPlaintextOnceAndPersistsOnlyHash() {
        Tenant tenant = new Tenant("Acme");
        UUID tenantId = tenant.getId();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(apiKeyGenerator.generate()).thenReturn(generatedKey());
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatedApiKeyResponse response = apiKeyService.createApiKey(
                new TenantPrincipal(tenantId, UUID.randomUUID()), "Production");

        ArgumentCaptor<ApiKey> keyCaptor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(keyCaptor.capture());
        ApiKey persistedKey = keyCaptor.getValue();
        assertEquals(KEY_HASH, persistedKey.getKeyHash());
        assertNotEquals(PLAINTEXT_KEY, persistedKey.getKeyHash());
        assertFalse(persistedKey.toString().contains(PLAINTEXT_KEY));
        assertEquals(tenantId, persistedKey.getTenant().getId());
        assertEquals("Production", persistedKey.getName());
        assertEquals(ApiKeyStatus.ACTIVE, persistedKey.getStatus());
        assertEquals(PLAINTEXT_KEY, response.apiKey());
        assertFalse(response.toString().contains(PLAINTEXT_KEY));
        assertEquals(KEY_PREFIX, response.keyPrefix());
        verify(apiKeyGenerator).generate();
    }

    @Test
    void trimsNameBeforePersisting() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(new Tenant("Acme")));
        when(apiKeyGenerator.generate()).thenReturn(generatedKey());
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        apiKeyService.createApiKey(new TenantPrincipal(tenantId, UUID.randomUUID()), "  Production key  ");

        ArgumentCaptor<ApiKey> keyCaptor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(keyCaptor.capture());
        assertEquals("Production key", keyCaptor.getValue().getName());
    }

    @Test
    void rejectsUnknownTenantBeforeGeneratingOrSavingKey() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> apiKeyService.createApiKey(
                new TenantPrincipal(tenantId, UUID.randomUUID()), "Production"));

        verify(tenantRepository).findById(tenantId);
        verifyNoInteractions(apiKeyGenerator, apiKeyRepository);
    }

    @Test
    void rejectsBlankAndOverlongNames() {
        UUID tenantId = UUID.randomUUID();

        TenantPrincipal principal = new TenantPrincipal(tenantId, UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> apiKeyService.createApiKey(principal, " \t "));
        assertThrows(IllegalArgumentException.class, () -> apiKeyService.createApiKey(principal, "x".repeat(101)));

        verifyNoInteractions(tenantRepository, apiKeyRepository, apiKeyGenerator);
    }

    @Test
    void listsOnlyKeysForAuthenticatedTenantAndDoesNotExposeHashes() {
        UUID tenantId = UUID.randomUUID();
        ApiKey key = new ApiKey(new Tenant("Acme"), "Production", KEY_PREFIX, KEY_HASH);
        when(apiKeyRepository.findAllByTenant_Id(tenantId)).thenReturn(List.of(key));

        List<ApiKeyResponse> responses = apiKeyService.getApiKeys(new TenantPrincipal(tenantId, UUID.randomUUID()));

        verify(apiKeyRepository).findAllByTenant_Id(tenantId);
        assertEquals(1, responses.size());
        ApiKeyResponse response = responses.get(0);
        assertEquals(key.getId(), response.id());
        assertEquals(key.getName(), response.name());
        assertEquals(key.getKeyPrefix(), response.keyPrefix());
        assertEquals(ApiKeyStatus.ACTIVE, response.status());
        assertFalse(response.toString().contains(KEY_HASH));
    }

    @Test
    void rejectsApiKeyListingWithoutAuthenticatedTenantPrincipal() {
        assertThrows(IllegalArgumentException.class, () -> apiKeyService.getApiKeys(null));

        verifyNoInteractions(apiKeyRepository);
    }

    @Test
    void revokesTenantOwnedKeyAndReturnsRevokedResponse() {
        UUID tenantId = UUID.randomUUID();
        ApiKey key = new ApiKey(new Tenant("Acme"), "Production", KEY_PREFIX, KEY_HASH);
        when(apiKeyRepository.findByIdAndTenant_Id(key.getId(), tenantId)).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(key)).thenReturn(key);

        ApiKeyResponse response = apiKeyService.revokeApiKey(
                new TenantPrincipal(tenantId, UUID.randomUUID()), key.getId());

        assertEquals(ApiKeyStatus.REVOKED, response.status());
        assertNotNull(response.revokedAt());
        verify(apiKeyRepository).findByIdAndTenant_Id(key.getId(), tenantId);
        verify(apiKeyRepository).save(key);
    }

    @Test
    void cannotRevokeKeyOwnedByAnotherTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID apiKeyId = UUID.randomUUID();
        when(apiKeyRepository.findByIdAndTenant_Id(apiKeyId, tenantId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> apiKeyService.revokeApiKey(
                new TenantPrincipal(tenantId, UUID.randomUUID()), apiKeyId));

        verify(apiKeyRepository).findByIdAndTenant_Id(apiKeyId, tenantId);
        verify(apiKeyRepository, never()).save(any(ApiKey.class));
    }

    @Test
    void rejectsRepeatedRevocation() {
        UUID tenantId = UUID.randomUUID();
        ApiKey key = new ApiKey(new Tenant("Acme"), "Production", KEY_PREFIX, KEY_HASH);
        key.revoke(Instant.now());
        when(apiKeyRepository.findByIdAndTenant_Id(key.getId(), tenantId)).thenReturn(Optional.of(key));

        assertThrows(IllegalStateException.class, () -> apiKeyService.revokeApiKey(
                new TenantPrincipal(tenantId, UUID.randomUUID()), key.getId()));

        verify(apiKeyRepository).findByIdAndTenant_Id(key.getId(), tenantId);
        verify(apiKeyRepository, never()).save(any(ApiKey.class));
    }

    private ApiKeyGenerator.GeneratedApiKey generatedKey() {
        return new ApiKeyGenerator.GeneratedApiKey(PLAINTEXT_KEY, KEY_PREFIX, KEY_HASH);
    }
}



