package com.opensms.webhook.entity;

import com.opensms.message.entity.Message;
import com.opensms.tenant.entity.Tenant;
import com.opensms.webhook.dto.WebhookEventPayload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false, updatable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "webhook_subscription_id")
    private WebhookSubscription webhookSubscription;

    @Column(name = "subscription_id_snapshot", nullable = false, updatable = false)
    private UUID subscriptionIdSnapshot;

    @Column(name = "event_type", length = 40, nullable = false, updatable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_payload", columnDefinition = "jsonb", nullable = false, updatable = false)
    private WebhookEventPayload eventPayload;

    @Column(name = "destination_url_snapshot", length = 2048, nullable = false, updatable = false)
    private String destinationUrlSnapshot;

    @Column(name = "encrypted_signing_secret_snapshot", nullable = false, updatable = false,
            columnDefinition = "bytea")
    private byte[] encryptedSigningSecretSnapshot;

    @Column(name = "signing_secret_iv_snapshot", nullable = false, updatable = false, columnDefinition = "bytea")
    private byte[] signingSecretIvSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private WebhookDeliveryStatus status = WebhookDeliveryStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant updatedAt;

    protected WebhookDelivery() {
        // Required by JPA.
    }

    public WebhookDelivery(WebhookSubscription subscription, Message message, WebhookEventPayload eventPayload) {
        this.id = UUID.randomUUID();
        this.webhookSubscription = Objects.requireNonNull(subscription, "subscription must not be null");
        this.message = Objects.requireNonNull(message, "message must not be null");
        this.tenant = Objects.requireNonNull(message.getTenant(), "message tenant must not be null");
        if (!subscription.getTenant().getId().equals(tenant.getId())) {
            throw new IllegalArgumentException("Webhook subscription and message must belong to the same tenant.");
        }
        this.subscriptionIdSnapshot = subscription.getId();
        this.eventPayload = Objects.requireNonNull(eventPayload, "eventPayload must not be null");
        this.eventId = eventPayload.eventId();
        this.eventType = eventPayload.eventType().value();
        this.destinationUrlSnapshot = subscription.getUrl();
        this.encryptedSigningSecretSnapshot = subscription.getEncryptedSigningSecret();
        this.signingSecretIvSnapshot = subscription.getSigningSecretIv();
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public Tenant getTenant() { return tenant; }
    public Message getMessage() { return message; }
    public WebhookSubscription getWebhookSubscription() { return webhookSubscription; }
    public UUID getSubscriptionIdSnapshot() { return subscriptionIdSnapshot; }
    public WebhookEventType getEventType() { return WebhookEventType.fromValue(eventType); }
    public WebhookEventPayload getEventPayload() { return eventPayload; }
    public String getDestinationUrlSnapshot() { return destinationUrlSnapshot; }
    public byte[] getEncryptedSigningSecretSnapshot() { return encryptedSigningSecretSnapshot.clone(); }
    public byte[] getSigningSecretIvSnapshot() { return signingSecretIvSnapshot.clone(); }
    public WebhookDeliveryStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @PrePersist
    private void initializePersistenceFields() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        attemptCount = 0;
        status = WebhookDeliveryStatus.PENDING;
    }

    @PreUpdate
    private void updateTimestamp() { updatedAt = Instant.now(); }
}
