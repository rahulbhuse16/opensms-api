package com.opensms.message.dto;

import java.util.List;

public record MessagePageResponse(
        List<MessageResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {

    public MessagePageResponse {
        content = List.copyOf(content);
    }
}
