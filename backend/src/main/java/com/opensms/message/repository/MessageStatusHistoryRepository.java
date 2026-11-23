package com.opensms.message.repository;

import com.opensms.message.entity.MessageStatusHistory;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

public interface MessageStatusHistoryRepository extends Repository<MessageStatusHistory, UUID> {

    MessageStatusHistory save(MessageStatusHistory history);

    List<MessageStatusHistory> findAllByMessage_IdAndTenant_IdOrderByCreatedAtAsc(
            UUID messageId,
            UUID tenantId
    );
}
