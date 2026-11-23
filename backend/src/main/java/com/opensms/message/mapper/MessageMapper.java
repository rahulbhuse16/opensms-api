package com.opensms.message.mapper;

import com.opensms.message.dto.MessageResponse;
import com.opensms.message.entity.Message;
import org.springframework.stereotype.Component;

@Component
public class MessageMapper {

    public MessageResponse toResponse(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message must not be null.");
        }

        return new MessageResponse(
                message.getId(),
                message.getDestinationPhoneNumber(),
                message.getBody(),
                message.getStatus(),
                message.getCreatedAt(),
                message.getUpdatedAt()
        );
    }
}
