package com.opensms.outbox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_type", length = 100, nullable = false, updatable = false)
    private String eventType;

    @Column(name = "aggregate_type", length = 50, nullable = false, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_payload", nullable = false, columnDefinition = "JSONB", updatable = false)
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private OutboxEventStatus status = OutboxEventStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "available_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant availableAt;

    @Column(name = "claimed_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant claimedAt;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    @Column(name = "published_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant publishedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    protected OutboxEvent() {
        // Required by JPA.
    }

    public OutboxEvent(String eventType, String aggregateType, UUID aggregateId, Map<String, ?> payload) {
        this.id = UUID.randomUUID();
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Map<String, Object> payloadCopy = new HashMap<>();
        payloadCopy.putAll(Objects.requireNonNull(payload, "payload must not be null"));
        this.payload = Map.copyOf(payloadCopy);
        this.attemptCount = 0;
        this.availableAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public OutboxEventStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public UUID getClaimToken() {
        return claimToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public boolean isClaimedBy(UUID token) {
        return status == OutboxEventStatus.PROCESSING && claimToken != null && claimToken.equals(token);
    }

    public void claim(Instant claimedAt) {
        if (status != OutboxEventStatus.PENDING && status != OutboxEventStatus.PROCESSING) {
            throw new IllegalStateException("Only pending or expired processing events can be claimed.");
        }
        status = OutboxEventStatus.PROCESSING;
        attemptCount++;
        this.claimedAt = Objects.requireNonNull(claimedAt, "claimedAt must not be null");
        this.claimToken = UUID.randomUUID();
    }

    public void markPublished(UUID expectedClaimToken, Instant publishedAt) {
        requireClaim(expectedClaimToken);
        status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        lastError = null;
        clearClaim();
    }

    public void scheduleRetry(UUID expectedClaimToken, Instant availableAt, String error) {
        requireClaim(expectedClaimToken);
        status = OutboxEventStatus.PENDING;
        this.availableAt = Objects.requireNonNull(availableAt, "availableAt must not be null");
        lastError = Objects.requireNonNull(error, "error must not be null");
        clearClaim();
    }

    public void markFailed(UUID expectedClaimToken, String error) {
        requireClaim(expectedClaimToken);
        markTerminalFailure(error);
    }

    public void markTerminalFailure(String error) {
        status = OutboxEventStatus.FAILED;
        lastError = Objects.requireNonNull(error, "error must not be null");
        clearClaim();
    }

    @PrePersist
    private void initializePersistenceFields() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (availableAt == null) {
            availableAt = Instant.now();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    private void requireClaim(UUID expectedClaimToken) {
        if (!isClaimedBy(expectedClaimToken)) {
            throw new IllegalStateException("Outbox event claim is no longer current.");
        }
    }

    private void clearClaim() {
        claimedAt = null;
        claimToken = null;
    }
}
