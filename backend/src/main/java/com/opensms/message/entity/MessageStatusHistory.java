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
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "message_status_history")
public class MessageStatusHistory {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false, updatable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20, updatable = false)
    private MessageStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 20, nullable = false, updatable = false)
    private MessageStatus newStatus;

    @Column(name = "change_source", length = 50, nullable = false, updatable = false)
    private String changeSource;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    protected MessageStatusHistory() {
        // Required by JPA.
    }

    public MessageStatusHistory(
            Message message,
            MessageStatus previousStatus,
            MessageStatus newStatus,
            String changeSource
    ) {
        this.id = UUID.randomUUID();
        this.message = Objects.requireNonNull(message, "message must not be null");
        this.tenant = Objects.requireNonNull(message.getTenant(), "message tenant must not be null");
        this.previousStatus = previousStatus;
        this.newStatus = Objects.requireNonNull(newStatus, "newStatus must not be null");
        this.changeSource = Objects.requireNonNull(changeSource, "changeSource must not be null");
    }

    public UUID getId() {
        return id;
    }

    public Message getMessage() {
        return message;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public MessageStatus getPreviousStatus() {
        return previousStatus;
    }

    public MessageStatus getNewStatus() {
        return newStatus;
    }

    public String getChangeSource() {
        return changeSource;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    private void initializePersistenceFields() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
