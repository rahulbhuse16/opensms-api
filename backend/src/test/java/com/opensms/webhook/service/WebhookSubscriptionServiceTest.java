package com.opensms.webhook.service;

import com.opensms.security.TenantPrincipal;
import com.opensms.tenant.entity.Tenant;
import com.opensms.tenant.repository.TenantRepository;
import com.opensms.webhook.dto.CreateWebhookSubscriptionRequest;
import com.opensms.webhook.dto.CreatedWebhookSubscriptionResponse;
import com.opensms.webhook.dto.UpdateWebhookSubscriptionRequest;
import com.opensms.webhook.dto.WebhookSubscriptionResponse;
import com.opensms.webhook.entity.WebhookEventType;
import com.opensms.webhook.entity.WebhookSubscription;
import com.opensms.webhook.entity.WebhookSubscriptionStatus;
import com.opensms.webhook.mapper.WebhookSubscriptionMapper;
import com.opensms.webhook.repository.WebhookSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookSubscriptionServiceTest {

    private static final String KEY = java.util.Base64.getEncoder().encodeToString(new byte[32]);

    @Mock private WebhookSubscriptionRepository repository;
    @Mock private TenantRepository tenantRepository;

    private WebhookSigningSecretService secretService;
    private WebhookSubscriptionService service;

    @BeforeEach
    void setUp() {
        secretService = new WebhookSigningSecretService(KEY);
        service = new WebhookSubscriptionService(repository, tenantRepository, secretService,
                new WebhookSubscriptionMapper());
    }

    @Test
    void createReturnsSecretOnceAndPersistsOnlyEncryptedSecret() {
        Tenant tenant = new Tenant("Acme");
        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(repository.save(any(WebhookSubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatedWebhookSubscriptionResponse created = service.create(principal(tenant), createRequest());

        ArgumentCaptor<WebhookSubscription> captor = ArgumentCaptor.forClass(WebhookSubscription.class);
        verify(repository).save(captor.capture());
        WebhookSubscription saved = captor.getValue();
        assertEquals(tenant.getId(), saved.getTenant().getId());
        assertEquals("https://hooks.example.test/events", saved.getUrl());
        assertEquals(List.of(WebhookEventType.MESSAGE_QUEUED, WebhookEventType.MESSAGE_FAILED), saved.getEventTypes());
        assertTrue(created.signingSecret().startsWith("whsec_"));
        assertEquals(created.signingSecret(), secretService.decrypt(
                saved.getEncryptedSigningSecret(), saved.getSigningSecretIv()));
        assertFalse(new String(saved.getEncryptedSigningSecret(), java.nio.charset.StandardCharsets.UTF_8)
                .contains(created.signingSecret()));
    }

    @Test
    void listAndGetAreTenantScopedAndResponsesContainNoSecret() {
        Tenant tenant = new Tenant("Acme");
        WebhookSubscription subscription = subscription(tenant);
        byte[] encryptedSecretBeforeUpdate = subscription.getEncryptedSigningSecret();
        byte[] ivBeforeUpdate = subscription.getSigningSecretIv();
        when(repository.findAllByTenant_IdOrderByCreatedAtDesc(tenant.getId())).thenReturn(List.of(subscription));
        when(repository.findByIdAndTenant_Id(subscription.getId(), tenant.getId())).thenReturn(Optional.of(subscription));

        List<WebhookSubscriptionResponse> listed = service.list(principal(tenant));
        WebhookSubscriptionResponse fetched = service.get(principal(tenant), subscription.getId());

        assertEquals(1, listed.size());
        assertEquals(subscription.getId(), fetched.id());
        assertEquals("https://hooks.example.test/events", fetched.url());
        assertFalse(fetched.toString().contains("whsec_"));
        verify(repository).findAllByTenant_IdOrderByCreatedAtDesc(tenant.getId());
        verify(repository).findByIdAndTenant_Id(subscription.getId(), tenant.getId());
    }

    @Test
    void updateAndDeleteUseTenantScopedLookups() {
        Tenant tenant = new Tenant("Acme");
        WebhookSubscription subscription = subscription(tenant);
        when(repository.findByIdAndTenant_Id(subscription.getId(), tenant.getId())).thenReturn(Optional.of(subscription));
        when(repository.save(subscription)).thenReturn(subscription);

        WebhookSubscriptionResponse updated = service.update(principal(tenant), subscription.getId(),
                new UpdateWebhookSubscriptionRequest("https://hooks.example.test/new",
                        List.of(WebhookEventType.MESSAGE_SENT), WebhookSubscriptionStatus.SUSPENDED));
        service.delete(principal(tenant), subscription.getId());

        assertEquals("https://hooks.example.test/new", updated.url());
        assertEquals(WebhookSubscriptionStatus.SUSPENDED, updated.status());
        assertEquals(List.of(WebhookEventType.MESSAGE_SENT), updated.eventTypes());
        org.junit.jupiter.api.Assertions.assertArrayEquals(encryptedSecretBeforeUpdate,
                subscription.getEncryptedSigningSecret());
        org.junit.jupiter.api.Assertions.assertArrayEquals(ivBeforeUpdate, subscription.getSigningSecretIv());
        verify(repository).findByIdAndTenant_Id(subscription.getId(), tenant.getId());
        verify(repository).save(subscription);
        verify(repository).delete(subscription);
    }

    @Test
    void otherTenantCannotRetrieveUpdateOrDeleteSubscription() {
        Tenant otherTenant = new Tenant("Other");
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenant_Id(id, otherTenant.getId())).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.get(principal(otherTenant), id));
        assertThrows(NoSuchElementException.class, () -> service.update(principal(otherTenant), id,
                new UpdateWebhookSubscriptionRequest("https://hooks.example.test", List.of(WebhookEventType.MESSAGE_SENT),
                        WebhookSubscriptionStatus.ACTIVE)));
        assertThrows(NoSuchElementException.class, () -> service.delete(principal(otherTenant), id));
        verifyNoInteractions(tenantRepository);
    }

    private CreateWebhookSubscriptionRequest createRequest() {
        return new CreateWebhookSubscriptionRequest("https://hooks.example.test/events",
                List.of(WebhookEventType.MESSAGE_QUEUED, WebhookEventType.MESSAGE_FAILED));
    }

    private WebhookSubscription subscription(Tenant tenant) {
        var secret = secretService.generateAndEncrypt();
        return new WebhookSubscription(tenant, "https://hooks.example.test/events",
                List.of(WebhookEventType.MESSAGE_QUEUED), secret.ciphertext(), secret.iv());
    }

    private TenantPrincipal principal(Tenant tenant) {
        return new TenantPrincipal(tenant.getId(), UUID.randomUUID());
    }
}
