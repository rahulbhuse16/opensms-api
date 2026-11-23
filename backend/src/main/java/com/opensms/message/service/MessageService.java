package com.opensms.message.service;

import com.opensms.message.dto.CreateMessageRequest;
import com.opensms.message.dto.MessagePageResponse;
import com.opensms.message.dto.MessageResponse;
import com.opensms.message.dto.MessageStatusHistoryResponse;
import com.opensms.message.entity.Message;
import com.opensms.message.entity.MessageStatus;
import com.opensms.message.idempotency.IdempotencyKeyConflictException;
import com.opensms.message.idempotency.MessageIdempotencyRepository;
import com.opensms.message.idempotency.MessageIdempotencySupport;
import com.opensms.message.mapper.MessageMapper;
import com.opensms.message.repository.MessageRepository;
import com.opensms.outbox.entity.OutboxEvent;
import com.opensms.outbox.repository.OutboxEventRepository;
import com.opensms.security.TenantPrincipal;
import com.opensms.tenant.entity.Tenant;
import com.opensms.tenant.repository.TenantRepository;
import com.opensms.webhook.dto.WebhookEventPayload;
import com.opensms.webhook.entity.WebhookEventType;
import com.opensms.webhook.service.WebhookEventGenerationService;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class MessageService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MessageRepository messageRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TenantRepository tenantRepository;
    private final MessageMapper messageMapper;
    private final MessageStatusHistoryService statusHistoryService;
    private final WebhookEventGenerationService webhookEventGenerationService;
    private final MessageIdempotencyRepository idempotencyRepository;
    private final MessageIdempotencySupport idempotencySupport;

    public MessageService(
            MessageRepository messageRepository,
            OutboxEventRepository outboxEventRepository,
            TenantRepository tenantRepository,
            MessageMapper messageMapper,
            MessageStatusHistoryService statusHistoryService,
            WebhookEventGenerationService webhookEventGenerationService,
            MessageIdempotencyRepository idempotencyRepository,
            MessageIdempotencySupport idempotencySupport
    ) {
        this.messageRepository = messageRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.tenantRepository = tenantRepository;
        this.messageMapper = messageMapper;
        this.statusHistoryService = statusHistoryService;
        this.webhookEventGenerationService = webhookEventGenerationService;
        this.idempotencyRepository = idempotencyRepository;
        this.idempotencySupport = idempotencySupport;
    }

    @Transactional
    public MessageResponse createMessage(CreateMessageRequest request, TenantPrincipal principal) {
        return createMessage(request, principal, null);
    }

    @Transactional
    public MessageResponse createMessage(
            CreateMessageRequest request,
            TenantPrincipal principal,
            String idempotencyKey
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Message request must not be null.");
        }
        UUID tenantId = authenticatedTenantId(principal);
        String fingerprint = null;
        UUID messageId = null;
        Instant responseTimestamp = null;
        if (idempotencyKey != null) {
            idempotencySupport.validateKey(idempotencyKey);
            fingerprint = idempotencySupport.fingerprint(request);
            messageId = UUID.randomUUID();
            // PostgreSQL TIMESTAMP WITH TIME ZONE stores microsecond precision.
            responseTimestamp = Instant.now().truncatedTo(ChronoUnit.MICROS);
            int claimed = idempotencyRepository.claim(
                    UUID.randomUUID(), tenantId, idempotencyKey, fingerprint, messageId,
                    responseTimestamp, responseTimestamp);
            if (claimed == 0) {
                var existing = idempotencyRepository.findByTenant_IdAndIdempotencyKey(tenantId, idempotencyKey)
                        .orElseThrow(() -> new IllegalStateException(
                                "Idempotency record was not visible after a conflicting claim."));
                if (!existing.getRequestFingerprint().equals(fingerprint)) {
                    throw new IdempotencyKeyConflictException();
                }
                Message original = messageRepository.findByIdAndTenant_Id(existing.getMessageId(), tenantId)
                        .orElseThrow(() -> new IllegalStateException(
                                "The idempotency record references a missing message."));
                return new MessageResponse(
                        original.getId(), original.getDestinationPhoneNumber(), original.getBody(),
                        MessageStatus.QUEUED, existing.getResponseCreatedAt(), existing.getResponseUpdatedAt());
            }
            if (claimed != 1) {
                throw new IllegalStateException("Unable to claim idempotency key.");
            }
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("Tenant not found: " + tenantId));

        Message message = messageId == null
                ? new Message(tenant, request.destinationPhoneNumber(), request.body())
                : new Message(messageId, tenant, request.destinationPhoneNumber(), request.body(), responseTimestamp);
        Message savedMessage = messageId == null ? messageRepository.save(message) : messageRepository.saveAndFlush(message);
        statusHistoryService.recordInitialQueued(savedMessage);
        WebhookEventPayload payload = WebhookEventPayload.fromMessage(savedMessage, WebhookEventType.MESSAGE_QUEUED);
        webhookEventGenerationService.createDeliveries(
                tenantId, savedMessage.getId(), WebhookEventType.MESSAGE_QUEUED, payload);
        OutboxEvent outboxEvent = new OutboxEvent(
                "MESSAGE_QUEUED",
                "MESSAGE",
                savedMessage.getId(),
                Map.of("messageId", savedMessage.getId(), "tenantId", tenantId)
        );
        outboxEventRepository.save(outboxEvent);
        return messageMapper.toResponse(savedMessage);
    }

    @Transactional(readOnly = true)
    public MessagePageResponse getMessages(TenantPrincipal principal, int page, int size, MessageStatus status) {
        if (page < 0) {
            throw new IllegalArgumentException("Page number must not be negative.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        UUID tenantId = authenticatedTenantId(principal);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Message> messages = status == null
                ? messageRepository.findAllByTenant_Id(tenantId, pageable)
                : messageRepository.findAllByTenant_IdAndStatus(tenantId, status, pageable);
        Page<MessageResponse> responses = messages.map(messageMapper::toResponse);
        return new MessagePageResponse(
                responses.getContent(),
                responses.getNumber(),
                responses.getSize(),
                responses.getTotalElements(),
                responses.getTotalPages(),
                responses.hasNext(),
                responses.hasPrevious()
        );
    }

    @Transactional(readOnly = true)
    public MessageResponse getMessageById(UUID messageId, TenantPrincipal principal) {
        if (messageId == null) {
            throw new IllegalArgumentException("Message ID must not be null.");
        }
        UUID tenantId = authenticatedTenantId(principal);
        Message message = messageRepository.findByIdAndTenant_Id(messageId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Message not found: " + messageId));
        return messageMapper.toResponse(message);
    }

    @Transactional(readOnly = true)
    public List<MessageStatusHistoryResponse> getMessageHistory(
            UUID messageId,
            TenantPrincipal principal
    ) {
        return statusHistoryService.getHistory(messageId, authenticatedTenantId(principal));
    }

    private UUID authenticatedTenantId(TenantPrincipal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("Authenticated tenant principal must not be null.");
        }
        return principal.tenantId();
    }
}
