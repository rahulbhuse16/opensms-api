package com.opensms.webhook.entity;

import com.opensms.tenant.entity.Tenant;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "webhook_subscriptions")
public class WebhookSubscription {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "url", length = 2048, nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private WebhookSubscriptionStatus status = WebhookSubscriptionStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_types", columnDefinition = "jsonb", nullable = false)
    private List<String> eventTypes;

    @Column(name = "encrypted_signing_secret", nullable = false, updatable = false, columnDefinition = "bytea")
    private byte[] encryptedSigningSecret;

    @Column(name = "signing_secret_iv", nullable = false, updatable = false, columnDefinition = "bytea")
    private byte[] signingSecretIv;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant updatedAt;

    protected WebhookSubscription() {
        // Required by JPA.
    }

    public WebhookSubscription(
            Tenant tenant,
            String url,
            List<WebhookEventType> eventTypes,
            byte[] encryptedSigningSecret,
            byte[] signingSecretIv
    ) {
        this.id = UUID.randomUUID();
        this.tenant = Objects.requireNonNull(tenant, "tenant must not be null");
        this.url = requireUrl(url);
        this.eventTypes = toValues(eventTypes);
        this.encryptedSigningSecret = Objects.requireNonNull(encryptedSigningSecret,
                "encryptedSigningSecret must not be null").clone();
        this.signingSecretIv = Objects.requireNonNull(signingSecretIv, "signingSecretIv must not be null").clone();
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public String getUrl() { return url; }
    public WebhookSubscriptionStatus getStatus() { return status; }
    public List<WebhookEventType> getEventTypes() {
        return eventTypes.stream().map(WebhookEventType::fromValue).toList();
    }
    public byte[] getEncryptedSigningSecret() { return encryptedSigningSecret.clone(); }
    public byte[] getSigningSecretIv() { return signingSecretIv.clone(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateConfiguration(String url, List<WebhookEventType> eventTypes,
                                     WebhookSubscriptionStatus status) {
        this.url = requireUrl(url);
        this.eventTypes = toValues(eventTypes);
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    private static String requireUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Webhook URL must not be blank.");
        }
        return url;
    }

    private static List<String> toValues(List<WebhookEventType> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("At least one webhook event type is required.");
        }
        return values.stream().map(value -> Objects.requireNonNull(value, "event type must not be null").value())
                .distinct().toList();
    }

    @PrePersist
    private void initializePersistenceFields() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void updateTimestamp() { updatedAt = Instant.now(); }
}
