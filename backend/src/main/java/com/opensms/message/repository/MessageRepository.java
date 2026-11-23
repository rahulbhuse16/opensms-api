package com.opensms.message.repository;

import com.opensms.message.entity.Message;
import com.opensms.message.entity.MessageStatus;
import com.opensms.analytics.repository.DailyStatusCountProjection;
import com.opensms.analytics.repository.StatusCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findAllByTenant_IdOrderByCreatedAtDesc(UUID tenantId);

    Page<Message> findAllByTenant_Id(UUID tenantId, Pageable pageable);

    Page<Message> findAllByTenant_IdAndStatus(UUID tenantId, MessageStatus status, Pageable pageable);

    Optional<Message> findByIdAndTenant_Id(UUID id, UUID tenantId);

    List<Message> findAllByTenant_IdAndStatusOrderByCreatedAtAsc(UUID tenantId, MessageStatus status);

    long countByTenant_Id(UUID tenantId);

    @Query(value = "SELECT status AS \"status\", COUNT(*) AS \"messageCount\" "
            + "FROM messages WHERE tenant_id = :tenantId GROUP BY status", nativeQuery = true)
    List<StatusCountProjection> aggregateStatusCounts(@Param("tenantId") UUID tenantId);

    @Query(value = "SELECT status AS \"status\", COUNT(*) AS \"messageCount\" FROM messages "
            + "WHERE tenant_id = :tenantId AND created_at >= :fromInclusive AND created_at < :toExclusive "
            + "GROUP BY status", nativeQuery = true)
    List<StatusCountProjection> aggregateStatusCountsForPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive
    );

    @Query(value = "SELECT (created_at AT TIME ZONE 'UTC')::date AS \"day\", status AS \"status\", "
            + "COUNT(*) AS \"messageCount\" FROM messages "
            + "WHERE tenant_id = :tenantId AND created_at >= :fromInclusive AND created_at < :toExclusive "
            + "GROUP BY (created_at AT TIME ZONE 'UTC')::date, status "
            + "ORDER BY (created_at AT TIME ZONE 'UTC')::date ASC, status ASC", nativeQuery = true)
    List<DailyStatusCountProjection> aggregateDailyStatusCounts(
            @Param("tenantId") UUID tenantId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive
    );
}
