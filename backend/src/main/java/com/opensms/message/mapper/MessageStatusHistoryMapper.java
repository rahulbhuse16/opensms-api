package com.opensms.message.mapper;

import com.opensms.message.dto.MessageStatusHistoryResponse;
import com.opensms.message.entity.MessageStatusHistory;
import org.springframework.stereotype.Component;

@Component
public class MessageStatusHistoryMapper {

    public MessageStatusHistoryResponse toResponse(MessageStatusHistory history) {
        if (history == null) {
            throw new IllegalArgumentException("Message status history must not be null.");
        }
        return new MessageStatusHistoryResponse(
                history.getId(),
                history.getMessage().getId(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                history.getChangeSource(),
                history.getCreatedAt()
        );
    }
}
