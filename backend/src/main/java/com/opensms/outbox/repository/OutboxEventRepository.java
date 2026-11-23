package com.opensms.outbox.repository;

import com.opensms.outbox.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = "SELECT * FROM outbox_events "
            + "WHERE (status = 'PENDING' AND available_at <= :now) "
            + "OR (status = 'PROCESSING' AND claimed_at <= :staleBefore) "
            + "ORDER BY available_at ASC, created_at ASC "
            + "LIMIT :batchSize FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> lockDueOrAbandonedEvents(
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore,
            @Param("batchSize") int batchSize
    );

    @Query(value = "SELECT * FROM outbox_events WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<OutboxEvent> lockById(@Param("id") UUID id);
}
