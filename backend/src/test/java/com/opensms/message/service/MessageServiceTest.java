package com.opensms.message.service;

import com.opensms.message.dto.CreateMessageRequest;
import com.opensms.message.dto.MessagePageResponse;
import com.opensms.message.dto.MessageResponse;
import com.opensms.message.dto.MessageStatusHistoryResponse;
import com.opensms.message.entity.Message;
import com.opensms.message.entity.MessageStatus;
import com.opensms.message.idempotency.IdempotencyKeyConflictException;
import com.opensms.message.idempotency.MessageIdempotencyRecord;
import com.opensms.message.idempotency.MessageIdempotencyRepository;
import com.opensms.message.idempotency.MessageIdempotencySupport;
import com.opensms.message.mapper.MessageMapper;
import com.opensms.message.repository.MessageRepository;
import com.opensms.outbox.entity.OutboxEvent;
import com.opensms.outbox.entity.OutboxEventStatus;
import com.opensms.outbox.repository.OutboxEventRepository;
import com.opensms.security.TenantPrincipal;
import com.opensms.tenant.entity.Tenant;
import com.opensms.tenant.repository.TenantRepository;
import com.opensms.webhook.dto.WebhookEventPayload;
import com.opensms.webhook.entity.WebhookEventType;
import com.opensms.webhook.service.WebhookEventGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private MessageStatusHistoryService statusHistoryService;

    @Mock
    private WebhookEventGenerationService webhookEventGenerationService;

    @Mock
    private MessageIdempotencyRepository idempotencyRepository;

    private MessageIdempotencySupport idempotencySupport;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        idempotencySupport = new MessageIdempotencySupport();
        messageService = new MessageService(
                messageRepository, outboxEventRepository, tenantRepository, messageMapper, statusHistoryService,
                webhookEventGenerationService, idempotencyRepository, idempotencySupport);
    }

    @Test
    void keyedSubmissionClaimsTenantKeyAndPersistsMessageAndDependents() {
        Tenant tenant = new Tenant("Acme");
        TenantPrincipal principal = principal(tenant.getId());
        CreateMessageRequest request = new CreateMessageRequest("+14155552671", "Hello");
        when(idempotencyRepository.claim(any(UUID.class), eq(tenant.getId()), eq("request-1"),
                eq(idempotencySupport.fingerprint(request)), any(UUID.class), any(Instant.class), any(Instant.class)))
                .thenReturn(1);
        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(messageRepository.saveAndFlush(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MessageResponse response = response("+14155552671", "Hello", MessageStatus.QUEUED);
        when(messageMapper.toResponse(any(Message.class))).thenReturn(response);

        assertEquals(response, messageService.createMessage(request, principal, "request-1"));

        verify(idempotencyRepository).claim(any(UUID.class), eq(tenant.getId()), eq("request-1"),
                eq(idempotencySupport.fingerprint(request)), any(UUID.class), any(Instant.class), any(Instant.class));
        verify(messageRepository).saveAndFlush(any(Message.class));
        verify(statusHistoryService).recordInitialQueued(any(Message.class));
        verify(webhookEventGenerationService).createDeliveries(any(UUID.class), any(UUID.class),
                eq(WebhookEventType.MESSAGE_QUEUED), any(WebhookEventPayload.class));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void replayReturnsOriginalResponseWithoutCreatingDependentRows() {
        UUID tenantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        String fingerprint = idempotencySupport.fingerprint(new CreateMessageRequest("+14155552671", "Hello"));
        Instant initial = Instant.parse("2026-11-20T12:00:00.123456Z");
        MessageIdempotencyRecord record = org.mockito.Mockito.mock(MessageIdempotencyRecord.class);
        when(record.getRequestFingerprint()).thenReturn(fingerprint);
        when(record.getMessageId()).thenReturn(messageId);
        when(record.getResponseCreatedAt()).thenReturn(initial);
        when(record.getResponseUpdatedAt()).thenReturn(initial);
        when(idempotencyRepository.claim(any(UUID.class), eq(tenantId), eq("request-1"), eq(fingerprint),
                any(UUID.class), any(Instant.class), any(Instant.class))).thenReturn(0);
        when(idempotencyRepository.findByTenant_IdAndIdempotencyKey(tenantId, "request-1"))
                .thenReturn(Optional.of(record));
        when(messageRepository.findByIdAndTenant_Id(messageId, tenantId)).thenReturn(Optional.of(
                new Message(new Tenant("Acme"), "+14155552671", "Hello")));

        MessageResponse replay = messageService.createMessage(
                new CreateMessageRequest("+14155552671", "Hello"), principal(tenantId), "request-1");

        assertEquals(messageId, replay.id());
        assertEquals("+14155552671", replay.destinationPhoneNumber());
        assertEquals("Hello", replay.body());
        assertEquals(MessageStatus.QUEUED, replay.status());
        assertEquals(initial, replay.createdAt());
        assertEquals(initial, replay.updatedAt());
        verifyNoInteractions(tenantRepository, outboxEventRepository, statusHistoryService,
                webhookEventGenerationService, messageMapper);
        verify(messageRepository).findByIdAndTenant_Id(messageId, tenantId);
    }

    @Test
    void sameKeyWithDifferentPayloadConflictsBeforeCreatingAnything() {
        UUID tenantId = UUID.randomUUID();
        CreateMessageRequest request = new CreateMessageRequest("+14155552671", "different");
        MessageIdempotencyRecord record = org.mockito.Mockito.mock(MessageIdempotencyRecord.class);
        when(record.getRequestFingerprint()).thenReturn("0".repeat(64));
        when(idempotencyRepository.claim(any(UUID.class), eq(tenantId), eq("request-1"),
                eq(idempotencySupport.fingerprint(request)), any(UUID.class), any(Instant.class), any(Instant.class)))
                .thenReturn(0);
        when(idempotencyRepository.findByTenant_IdAndIdempotencyKey(tenantId, "request-1"))
                .thenReturn(Optional.of(record));

        assertThrows(IdempotencyKeyConflictException.class,
                () -> messageService.createMessage(request, principal(tenantId), "request-1"));
        verifyNoInteractions(messageRepository, tenantRepository, outboxEventRepository, statusHistoryService,
                webhookEventGenerationService, messageMapper);
    }

    @Test
    void sameKeyClaimIsIndependentForDifferentTenants() {
        UUID firstTenantId = UUID.randomUUID();
        UUID secondTenantId = UUID.randomUUID();
        CreateMessageRequest request = new CreateMessageRequest("+14155552671", "Hello");
        String fingerprint = idempotencySupport.fingerprint(request);
        when(idempotencyRepository.claim(any(UUID.class), eq(firstTenantId), eq("shared-key"), eq(fingerprint),
                any(UUID.class), any(Instant.class), any(Instant.class))).thenReturn(1);
        when(idempotencyRepository.claim(any(UUID.class), eq(secondTenantId), eq("shared-key"), eq(fingerprint),
                any(UUID.class), any(Instant.class), any(Instant.class))).thenReturn(1);
        Tenant firstTenant = org.mockito.Mockito.mock(Tenant.class);
        Tenant secondTenant = org.mockito.Mockito.mock(Tenant.class);
        when(firstTenant.getId()).thenReturn(firstTenantId);
        when(secondTenant.getId()).thenReturn(secondTenantId);
        when(tenantRepository.findById(firstTenantId)).thenReturn(Optional.of(firstTenant));
        when(tenantRepository.findById(secondTenantId)).thenReturn(Optional.of(secondTenant));
        when(messageRepository.saveAndFlush(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageMapper.toResponse(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            return new MessageResponse(message.getId(), message.getDestinationPhoneNumber(), message.getBody(),
                    message.getStatus(), message.getCreatedAt(), message.getUpdatedAt());
        });

        messageService.createMessage(request, principal(firstTenantId), "shared-key");
        messageService.createMessage(request, principal(secondTenantId), "shared-key");

        verify(idempotencyRepository).claim(any(UUID.class), eq(firstTenantId), eq("shared-key"), eq(fingerprint),
                any(UUID.class), any(Instant.class), any(Instant.class));
        verify(idempotencyRepository).claim(any(UUID.class), eq(secondTenantId), eq("shared-key"), eq(fingerprint),
                any(UUID.class), any(Instant.class), any(Instant.class));
        verify(messageRepository, org.mockito.Mockito.times(2)).saveAndFlush(any(Message.class));
        verify(outboxEventRepository, org.mockito.Mockito.times(2)).save(any(OutboxEvent.class));
    }

    @Test
    void dependentWriteFailurePropagatesForTransactionalRollback() {
        Tenant tenant = new Tenant("Acme");
        TenantPrincipal principal = principal(tenant.getId());
        CreateMessageRequest request = new CreateMessageRequest("+14155552671", "Hello");
        when(idempotencyRepository.claim(any(UUID.class), eq(tenant.getId()), eq("request-1"),
                eq(idempotencySupport.fingerprint(request)), any(UUID.class), any(Instant.class), any(Instant.class)))
                .thenReturn(1);
        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(messageRepository.saveAndFlush(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenThrow(new IllegalStateException("Outbox persistence failed."));

        assertThrows(IllegalStateException.class,
                () -> messageService.createMessage(request, principal, "request-1"));
        verify(idempotencyRepository).claim(any(UUID.class), eq(tenant.getId()), eq("request-1"),
                eq(idempotencySupport.fingerprint(request)), any(UUID.class), any(Instant.class), any(Instant.class));
        verify(messageRepository).saveAndFlush(any(Message.class));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
        // The production @Transactional method must roll back the key claim, message, history and webhook rows.
    }

    @Test
    void rejectsMalformedKeyBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> messageService.createMessage(new CreateMessageRequest("+14155552671", "Hello"),
                        principal(UUID.randomUUID()), " "));
        assertThrows(IllegalArgumentException.class,
                () -> messageService.createMessage(new CreateMessageRequest("+14155552671", "Hello"),
                        principal(UUID.randomUUID()), "x".repeat(129)));
        verifyNoInteractions(idempotencyRepository, tenantRepository, messageRepository);
    }

    @Test
    void createsQueuedMessageAndRecordsInitialHistory() {
        Tenant tenant = new Tenant("Acme");
        TenantPrincipal principal = principal(tenant.getId());
        CreateMessageRequest request = new CreateMessageRequest("+14155552671", "Hello");
        MessageResponse response = response("+14155552671", "Hello", MessageStatus.QUEUED);
        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageMapper.toResponse(any(Message.class))).thenReturn(response);

        MessageResponse result = messageService.createMessage(request, principal);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        Message savedMessage = messageCaptor.getValue();
        assertEquals(tenant.getId(), savedMessage.getTenant().getId());
        assertEquals("+14155552671", savedMessage.getDestinationPhoneNumber());
        assertEquals("Hello", savedMessage.getBody());
        assertEquals(MessageStatus.QUEUED, savedMessage.getStatus());
        assertEquals(response, result);
        verify(statusHistoryService).recordInitialQueued(savedMessage);
        verify(webhookEventGenerationService).createDeliveries(
                eq(tenant.getId()), eq(savedMessage.getId()), eq(WebhookEventType.MESSAGE_QUEUED),
                org.mockito.ArgumentMatchers.argThat(payload -> payload.eventType() == WebhookEventType.MESSAGE_QUEUED
                        && payload.messageStatus() == MessageStatus.QUEUED
                        && payload.messageId().equals(savedMessage.getId())
                        && payload.tenantId().equals(tenant.getId())));

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent savedEvent = eventCaptor.getValue();
        assertEquals("MESSAGE_QUEUED", savedEvent.getEventType());
        assertEquals("MESSAGE", savedEvent.getAggregateType());
        assertEquals(savedMessage.getId(), savedEvent.getAggregateId());
        assertEquals(OutboxEventStatus.PENDING, savedEvent.getStatus());
        assertEquals(Map.of("messageId", savedMessage.getId(), "tenantId", tenant.getId()),
                savedEvent.getPayload());
        verifyNoInteractions(idempotencyRepository);
    }

    @Test
    void propagatesOutboxPersistenceFailureAfterMessageSaveAttempt() {
        Tenant tenant = new Tenant("Acme");
        TenantPrincipal principal = principal(tenant.getId());
        CreateMessageRequest request = new CreateMessageRequest("+14155552671", "Hello");
        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenThrow(new IllegalStateException("Outbox persistence failed."));

        assertThrows(IllegalStateException.class, () -> messageService.createMessage(request, principal));

        verify(messageRepository).save(any(Message.class));
        verify(statusHistoryService).recordInitialQueued(any(Message.class));
        verify(webhookEventGenerationService).createDeliveries(
                any(UUID.class), any(UUID.class), eq(WebhookEventType.MESSAGE_QUEUED), any(WebhookEventPayload.class));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void listsNewestMessagesForAuthenticatedTenantWithDefaultPageValues() {
        UUID tenantId = UUID.randomUUID();
        TenantPrincipal principal = principal(tenantId);
        Message message = new Message(new Tenant("Acme"), "+14155552671", "Hello");
        MessageResponse response = response("+14155552671", "Hello", MessageStatus.QUEUED);
        when(messageRepository.findAllByTenant_Id(eq(tenantId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(message), PageRequest.of(0, 20), 41));
        when(messageMapper.toResponse(message)).thenReturn(response);

        MessagePageResponse result = messageService.getMessages(principal, 0, 20, null);

        assertEquals(List.of(response), result.content());
        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(41, result.totalElements());
        assertEquals(3, result.totalPages());
        assertTrue(result.hasNext());
        assertFalse(result.hasPrevious());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(messageRepository).findAllByTenant_Id(eq(tenantId), pageableCaptor.capture());
        assertEquals(Sort.Direction.DESC, pageableCaptor.getValue().getSort().getOrderFor("createdAt").getDirection());
        verify(messageMapper).toResponse(message);
    }

    @Test
    void appliesTenantAndStatusFiltersForCustomPage() {
        UUID tenantId = UUID.randomUUID();
        TenantPrincipal principal = principal(tenantId);
        when(messageRepository.findAllByTenant_IdAndStatus(eq(tenantId), eq(MessageStatus.FAILED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 10), 0));

        MessagePageResponse result = messageService.getMessages(principal, 2, 10, MessageStatus.FAILED);

        assertEquals(2, result.page());
        assertEquals(10, result.size());
        assertEquals(0, result.totalElements());
        assertFalse(result.hasNext());
        assertTrue(result.hasPrevious());
        verify(messageRepository).findAllByTenant_IdAndStatus(eq(tenantId), eq(MessageStatus.FAILED), any(Pageable.class));
    }

    @Test
    void rejectsInvalidPageAndPageSize() {
        TenantPrincipal principal = principal(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> messageService.getMessages(principal, -1, 20, null));
        assertThrows(IllegalArgumentException.class, () -> messageService.getMessages(principal, 0, 0, null));
        assertThrows(IllegalArgumentException.class, () -> messageService.getMessages(principal, 0, 101, null));

        verifyNoInteractions(messageRepository);
    }

    @Test
    void returnsEmptyPageWhenTenantHasNoMessages() {
        UUID tenantId = UUID.randomUUID();
        when(messageRepository.findAllByTenant_Id(eq(tenantId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        MessagePageResponse result = messageService.getMessages(principal(tenantId), 0, 20, null);

        assertEquals(List.of(), result.content());
        assertEquals(0, result.totalElements());
    }

    @Test
    void doesNotFindMessageOutsideAuthenticatedTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(messageRepository.findByIdAndTenant_Id(messageId, tenantId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> messageService.getMessageById(messageId, principal(tenantId)));

        verify(messageRepository).findByIdAndTenant_Id(messageId, tenantId);
    }

    @Test
    void retrievesHistoryUsingAuthenticatedTenantId() {
        UUID tenantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        MessageStatusHistoryResponse history = new MessageStatusHistoryResponse(
                UUID.randomUUID(), messageId, null, MessageStatus.QUEUED, "API", null);
        when(statusHistoryService.getHistory(messageId, tenantId)).thenReturn(List.of(history));

        List<MessageStatusHistoryResponse> result = messageService.getMessageHistory(messageId, principal(tenantId));

        assertEquals(List.of(history), result);
        verify(statusHistoryService).getHistory(messageId, tenantId);
    }

    private TenantPrincipal principal(UUID tenantId) {
        return new TenantPrincipal(tenantId, UUID.randomUUID());
    }

    private MessageResponse response(String destination, String body, MessageStatus status) {
        return new MessageResponse(UUID.randomUUID(), destination, body, status, null, null);
    }
}
