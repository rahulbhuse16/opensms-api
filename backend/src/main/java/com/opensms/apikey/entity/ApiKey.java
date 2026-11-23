package com.opensms.apikey.entity;

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
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "key_prefix", length = 16, nullable = false)
    private String keyPrefix;

    @Column(name = "key_hash", length = 64, nullable = false)
    private String keyHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ApiKeyStatus status = ApiKeyStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    @Column(name = "revoked_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant revokedAt;

    protected ApiKey() {
        // Required by JPA.
    }

    public ApiKey(Tenant tenant, String name, String keyPrefix, String keyHash) {
        this.id = UUID.randomUUID();
        this.tenant = Objects.requireNonNull(tenant, "tenant must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix must not be null");
        this.keyHash = Objects.requireNonNull(keyHash, "keyHash must not be null");
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public String getName() {
        return name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public ApiKeyStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void revoke(Instant revokedAt) {
        if (status == ApiKeyStatus.REVOKED) {
            throw new IllegalStateException("API key has already been revoked.");
        }
        Instant revocationTime = Objects.requireNonNull(revokedAt, "revokedAt must not be null");
        this.status = ApiKeyStatus.REVOKED;
        this.revokedAt = revocationTime;
    }

    @PrePersist
    private void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

