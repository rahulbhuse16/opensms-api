package com.opensms.message.service;

import com.opensms.message.dto.MessageStatusHistoryResponse;
import com.opensms.message.entity.Message;
import com.opensms.message.entity.MessageStatus;
import com.opensms.message.entity.MessageStatusHistory;
import com.opensms.message.mapper.MessageStatusHistoryMapper;
import com.opensms.message.repository.MessageRepository;
import com.opensms.message.repository.MessageStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class MessageStatusHistoryService {

    private static final String API_SOURCE = "API";

    private final MessageStatusHistoryRepository historyRepository;
    private final MessageRepository messageRepository;
    private final MessageStatusHistoryMapper historyMapper;

    public MessageStatusHistoryService(
            MessageStatusHistoryRepository historyRepository,
            MessageRepository messageRepository,
            MessageStatusHistoryMapper historyMapper
    ) {
        this.historyRepository = historyRepository;
        this.messageRepository = messageRepository;
        this.historyMapper = historyMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordInitialQueued(Message message) {
        if (message.getStatus() != MessageStatus.QUEUED) {
            throw new IllegalStateException("New messages must start in QUEUED status.");
        }
        historyRepository.save(new MessageStatusHistory(message, null, MessageStatus.QUEUED, API_SOURCE));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean transition(Message message, MessageStatus newStatus, String source) {
        MessageStatus previousStatus = message.getStatus();
        if (previousStatus == newStatus) {
            return false;
        }
        message.changeStatus(newStatus);
        historyRepository.save(new MessageStatusHistory(message, previousStatus, newStatus, source));
        return true;
    }

    @Transactional(readOnly = true)
    public List<MessageStatusHistoryResponse> getHistory(UUID messageId, UUID tenantId) {
        if (messageId == null || tenantId == null) {
            throw new IllegalArgumentException("Message ID and tenant ID must not be null.");
        }
        messageRepository.findByIdAndTenant_Id(messageId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Message not found: " + messageId));
        return historyRepository.findAllByMessage_IdAndTenant_IdOrderByCreatedAtAsc(messageId, tenantId).stream()
                .map(historyMapper::toResponse)
                .toList();
    }
}
