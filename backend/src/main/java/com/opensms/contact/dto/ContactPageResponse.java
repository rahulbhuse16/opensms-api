package com.opensms.contact.dto;

import java.util.List;

public record ContactPageResponse(
        List<ContactResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    public ContactPageResponse {
        content = List.copyOf(content);
    }
}
