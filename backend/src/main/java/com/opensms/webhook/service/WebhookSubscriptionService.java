package com.opensms.webhook.service;

import com.opensms.security.TenantPrincipal;
import com.opensms.tenant.entity.Tenant;
import com.opensms.tenant.repository.TenantRepository;
import com.opensms.webhook.dto.CreateWebhookSubscriptionRequest;
import com.opensms.webhook.dto.CreatedWebhookSubscriptionResponse;
import com.opensms.webhook.dto.UpdateWebhookSubscriptionRequest;
import com.opensms.webhook.dto.WebhookSubscriptionResponse;
import com.opensms.webhook.entity.WebhookSubscription;
import com.opensms.webhook.mapper.WebhookSubscriptionMapper;
import com.opensms.webhook.repository.WebhookSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class WebhookSubscriptionService {

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final WebhookSigningSecretService signingSecretService;
    private final WebhookSubscriptionMapper mapper;

    public WebhookSubscriptionService(
            WebhookSubscriptionRepository subscriptionRepository,
            TenantRepository tenantRepository,
            WebhookSigningSecretService signingSecretService,
            WebhookSubscriptionMapper mapper
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.tenantRepository = tenantRepository;
        this.signingSecretService = signingSecretService;
        this.mapper = mapper;
    }

    @Transactional
    public CreatedWebhookSubscriptionResponse create(TenantPrincipal principal,
                                                      CreateWebhookSubscriptionRequest request) {
        UUID tenantId = tenantId(principal);
        if (request == null || !request.isValidHttpsUrl()) {
            throw new IllegalArgumentException("A valid HTTPS webhook URL is required.");
        }
        if (request.eventTypes() == null || request.eventTypes().isEmpty()
                || request.eventTypes().stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Select at least one supported webhook event type.");
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("Tenant not found: " + tenantId));
        WebhookSigningSecretService.EncryptedSecret secret = signingSecretService.generateAndEncrypt();
        WebhookSubscription saved = subscriptionRepository.save(new WebhookSubscription(
                tenant, request.url(), request.eventTypes(), secret.ciphertext(), secret.iv()));
        WebhookSubscriptionResponse response = mapper.toResponse(saved);
        return new CreatedWebhookSubscriptionResponse(
                response.id(), response.url(), response.eventTypes(), response.status(),
                response.createdAt(), response.updatedAt(), secret.plaintext());
    }

    @Transactional(readOnly = true)
    public List<WebhookSubscriptionResponse> list(TenantPrincipal principal) {
        UUID tenantId = tenantId(principal);
        return subscriptionRepository.findAllByTenant_IdOrderByCreatedAtDesc(tenantId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WebhookSubscriptionResponse get(TenantPrincipal principal, UUID id) {
        return mapper.toResponse(findOwned(tenantId(principal), id));
    }

    @Transactional
    public WebhookSubscriptionResponse update(TenantPrincipal principal, UUID id,
                                               UpdateWebhookSubscriptionRequest request) {
        if (request == null || !request.isValidHttpsUrl()) {
            throw new IllegalArgumentException("A valid HTTPS webhook URL is required.");
        }
        if (request.eventTypes() == null || request.eventTypes().isEmpty()
                || request.eventTypes().stream().anyMatch(java.util.Objects::isNull)
                || request.status() == null) {
            throw new IllegalArgumentException("Event types and subscription status are required.");
        }
        WebhookSubscription subscription = findOwned(tenantId(principal), id);
        subscription.updateConfiguration(request.url(), request.eventTypes(), request.status());
        return mapper.toResponse(subscriptionRepository.save(subscription));
    }

    @Transactional
    public void delete(TenantPrincipal principal, UUID id) {
        subscriptionRepository.delete(findOwned(tenantId(principal), id));
    }

    private WebhookSubscription findOwned(UUID tenantId, UUID id) {
        if (id == null) throw new IllegalArgumentException("Webhook subscription ID must not be null.");
        return subscriptionRepository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Webhook subscription not found: " + id));
    }

    private UUID tenantId(TenantPrincipal principal) {
        if (principal == null) throw new IllegalArgumentException("Authenticated tenant principal must not be null.");
        return principal.tenantId();
    }
}
