package com.opensms.message.entity;

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

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "destination_phone_number", length = 20, nullable = false)
    private String destinationPhoneNumber;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private MessageStatus status = MessageStatus.QUEUED;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant updatedAt;

    protected Message() {
        // Required by JPA.
    }

    public Message(Tenant tenant, String destinationPhoneNumber, String body) {
        this(UUID.randomUUID(), tenant, destinationPhoneNumber, body, null);
    }

    public Message(UUID id, Tenant tenant, String destinationPhoneNumber, String body, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenant = Objects.requireNonNull(tenant, "tenant must not be null");
        this.destinationPhoneNumber = Objects.requireNonNull(
                destinationPhoneNumber, "destinationPhoneNumber must not be null");
        this.body = Objects.requireNonNull(body, "body must not be null");
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public String getDestinationPhoneNumber() {
        return destinationPhoneNumber;
    }

    public String getBody() {
        return body;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void changeStatus(MessageStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    private void initializePersistenceFields() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    private void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
