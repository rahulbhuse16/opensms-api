package com.opensms.message.controller;

import com.opensms.message.dto.CreateMessageRequest;
import com.opensms.message.dto.MessagePageResponse;
import com.opensms.message.dto.MessageResponse;
import com.opensms.message.dto.MessageStatusHistoryResponse;
import com.opensms.message.entity.MessageStatus;
import com.opensms.message.service.MessageService;
import com.opensms.security.TenantPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public ResponseEntity<MessageResponse> createMessage(
            @Valid @RequestBody CreateMessageRequest request,
            @AuthenticationPrincipal TenantPrincipal principal,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        MessageResponse response = messageService.createMessage(request, principal, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    public ResponseEntity<MessagePageResponse> getMessages(
            @AuthenticationPrincipal TenantPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) MessageStatus status
    ) {
        return ResponseEntity.ok(messageService.getMessages(principal, page, size, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MessageResponse> getMessageById(
            @PathVariable UUID id,
            @AuthenticationPrincipal TenantPrincipal principal
    ) {
        return ResponseEntity.ok(messageService.getMessageById(id, principal));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<MessageStatusHistoryResponse>> getMessageHistory(
            @PathVariable UUID id,
            @AuthenticationPrincipal TenantPrincipal principal
    ) {
        return ResponseEntity.ok(messageService.getMessageHistory(id, principal));
    }
}
