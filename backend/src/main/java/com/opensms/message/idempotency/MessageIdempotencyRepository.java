package com.opensms.message.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MessageIdempotencyRepository extends JpaRepository<MessageIdempotencyRecord, UUID> {

    @Modifying
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "INSERT INTO message_idempotency_keys "
            + "(id, tenant_id, idempotency_key, request_fingerprint, message_id, "
            + "response_created_at, response_updated_at) "
            + "VALUES (:id, :tenantId, :key, :fingerprint, :messageId, :responseCreatedAt, :responseUpdatedAt) "
            + "ON CONFLICT (tenant_id, idempotency_key) DO NOTHING",
            nativeQuery = true)
    int claim(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("key") String key,
            @Param("fingerprint") String fingerprint,
            @Param("messageId") UUID messageId,
            @Param("responseCreatedAt") Instant responseCreatedAt,
            @Param("responseUpdatedAt") Instant responseUpdatedAt
    );

    Optional<MessageIdempotencyRecord> findByTenant_IdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
