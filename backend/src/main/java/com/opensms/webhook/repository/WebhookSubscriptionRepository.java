package com.opensms.webhook.repository;

import com.opensms.webhook.entity.WebhookSubscription;
import com.opensms.webhook.entity.WebhookSubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {

    List<WebhookSubscription> findAllByTenant_IdOrderByCreatedAtDesc(UUID tenantId);

    List<WebhookSubscription> findAllByTenant_IdAndStatus(UUID tenantId, WebhookSubscriptionStatus status);

    Optional<WebhookSubscription> findByIdAndTenant_Id(UUID id, UUID tenantId);
}
