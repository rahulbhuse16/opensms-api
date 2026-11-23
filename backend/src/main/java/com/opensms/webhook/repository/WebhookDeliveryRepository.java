package com.opensms.webhook.repository;

import com.opensms.webhook.entity.WebhookDelivery;
import com.opensms.webhook.entity.WebhookDeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    boolean existsByEventIdAndSubscriptionIdSnapshotAndTenant_Id(
            UUID eventId,
            UUID subscriptionIdSnapshot,
            UUID tenantId
    );

    Page<WebhookDelivery> findAllByTenant_IdAndStatusOrderByCreatedAtAsc(
            UUID tenantId,
            WebhookDeliveryStatus status,
            Pageable pageable
    );

    List<WebhookDelivery> findAllByTenant_IdAndSubscriptionIdSnapshotOrderByCreatedAtDesc(
            UUID tenantId,
            UUID subscriptionIdSnapshot
    );
}
