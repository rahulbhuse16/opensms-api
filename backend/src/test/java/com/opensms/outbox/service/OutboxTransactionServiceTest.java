package com.opensms.outbox.service;

import com.opensms.message.entity.Message;
import com.opensms.message.entity.MessageStatus;
import com.opensms.message.repository.MessageRepository;
import com.opensms.message.service.MessageStatusHistoryService;
import com.opensms.outbox.config.OutboxDispatcherProperties;
import com.opensms.outbox.entity.OutboxEvent;
import com.opensms.outbox.entity.OutboxEventStatus;
import com.opensms.outbox.repository.OutboxEventRepository;
import com.opensms.tenant.entity.Tenant;
import com.opensms.webhook.dto.WebhookEventPayload;
import com.opensms.webhook.entity.WebhookEventType;
import com.opensms.webhook.service.WebhookEventGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Query;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxTransactionServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MessageStatusHistoryService statusHistoryService;

    @Mock
    private WebhookEventGenerationService webhookEventGenerationService;

    private OutboxDispatcherProperties properties;
    private OutboxTransactionService transactionService;

    @BeforeEach
    void setUp() {
        properties = new OutboxDispatcherProperties();
        properties.setMaxAttempts(3);
        properties.setInitialBackoffMs(1_000);
        properties.setMaxBackoffMs(5_000);
        doAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            MessageStatus newStatus = invocation.getArgument(1);
            if (message.getStatus() != newStatus) {
                message.changeStatus(newStatus);
                return true;
            }
            return false;
        }).when(statusHistoryService).transition(any(Message.class), any(MessageStatus.class), anyString());
        transactionService = new OutboxTransactionService(
                outboxEventRepository, messageRepository, statusHistoryService, webhookEventGenerationService,
                properties);
    }

    @Test
    void claimsDueEventAndIncrementsAttemptCount() {
        UUID tenantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        OutboxEvent event = queuedEvent(messageId, tenantId);
        when(outboxEventRepository.lockDueOrAbandonedEvents(any(), any(), eq(properties.getBatchSize())))
                .thenReturn(List.of(event));
        when(outboxEventRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<OutboxEvent> claimed = transactionService.claimDueEvents();

        assertEquals(1, claimed.size());
        assertEquals(OutboxEventStatus.PROCESSING, event.getStatus());
        assertEquals(1, event.getAttemptCount());
        assertNotNull(event.getClaimedAt());
        assertNotNull(event.getClaimToken());
        verify(outboxEventRepository).lockDueOrAbandonedEvents(any(), any(), eq(properties.getBatchSize()));
        verify(outboxEventRepository).saveAll(anyList());
    }

    @Test
    void simulatedAcceptanceMarksMessageSimulatedAndEventPublished() {
        Tenant tenant = new Tenant("Acme");
        UUID tenantId = tenant.getId();
        Message message = new Message(tenant, "+14155552671", "Hello");
        OutboxEvent event = queuedEvent(message.getId(), tenantId);
        event.claim(Instant.now());
        when(outboxEventRepository.lockById(event.getId())).thenReturn(Optional.of(event));
        when(messageRepository.findByIdAndTenant_Id(message.getId(), tenantId)).thenReturn(Optional.of(message));

        transactionService.recordAccepted(
                event.getId(), event.getClaimToken(), new OutboxMessageReference(message.getId(), tenantId), true);

        assertEquals(MessageStatus.SIMULATED, message.getStatus());
        assertEquals(OutboxEventStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        assertNull(event.getClaimToken());
        assertNull(event.getClaimedAt());
        verify(messageRepository).findByIdAndTenant_Id(message.getId(), tenantId);
        verify(messageRepository).save(message);
        verify(outboxEventRepository).save(event);
        verify(statusHistoryService).transition(message, MessageStatus.SIMULATED, "OUTBOX_DISPATCHER");
        verify(webhookEventGenerationService).createDeliveries(
                eq(tenantId), eq(message.getId()), eq(WebhookEventType.MESSAGE_SIMULATED),
                any(WebhookEventPayload.class));
    }

    @Test
    void realGatewayAcceptanceMarksMessageSentButNotDelivered() {
        Tenant tenant = new Tenant("Acme");
        UUID tenantId = tenant.getId();
        Message message = new Message(tenant, "+14155552671", "Hello");
        OutboxEvent event = queuedEvent(message.getId(), tenantId);
        event.claim(Instant.now());
        when(outboxEventRepository.lockById(event.getId())).thenReturn(Optional.of(event));
        when(messageRepository.findByIdAndTenant_Id(message.getId(), tenantId)).thenReturn(Optional.of(message));

        transactionService.recordAccepted(
                event.getId(), event.getClaimToken(), new OutboxMessageReference(message.getId(), tenantId), false);

        assertEquals(MessageStatus.SENT, message.getStatus());
        assertTrue(message.getStatus() != MessageStatus.DELIVERED);
        assertEquals(OutboxEventStatus.PUBLISHED, event.getStatus());
        verify(statusHistoryService).transition(message, MessageStatus.SENT, "OUTBOX_DISPATCHER");
        verify(webhookEventGenerationService).createDeliveries(
                eq(tenantId), eq(message.getId()), eq(WebhookEventType.MESSAGE_SENT), any(WebhookEventPayload.class));
    }

    @Test
    void unchangedMessageStatusDoesNotGenerateAnotherWebhookEvent() {
        Tenant tenant = new Tenant("Acme");
        UUID tenantId = tenant.getId();
        Message message = new Message(tenant, "+14155552671", "Hello");
        message.changeStatus(MessageStatus.SIMULATED);
        OutboxEvent event = queuedEvent(message.getId(), tenantId);
        event.claim(Instant.now());
        when(outboxEventRepository.lockById(event.getId())).thenReturn(Optional.of(event));
        when(messageRepository.findByIdAndTenant_Id(message.getId(), tenantId)).thenReturn(Optional.of(message));

        transactionService.recordAccepted(
                event.getId(), event.getClaimToken(), new OutboxMessageReference(message.getId(), tenantId), true);

        org.mockito.Mockito.verifyNoInteractions(webhookEventGenerationService);
    }

    @Test
    void transientFailureSchedulesBoundedRetryWithoutFailingMessage() {
        Tenant tenant = new Tenant("Acme");
        UUID tenantId = tenant.getId();
        Message message = new Message(tenant, "+14155552671", "Hello");
        OutboxEvent event = queuedEvent(message.getId(), tenantId);
        event.claim(Instant.now());
        when(outboxEventRepository.lockById(event.getId())).thenReturn(Optional.of(event));
        when(messageRepository.findByIdAndTenant_Id(message.getId(), tenantId)).thenReturn(Optional.of(message));
        Instant before = Instant.now();

        transactionService.recordFailure(
                event.getId(), event.getClaimToken(), new OutboxMessageReference(message.getId(), tenantId),
                "Gateway submission failed.");

        long retryDelay = Duration.between(before, event.getAvailableAt()).toMillis();
        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertEquals(1, event.getAttemptCount());
        assertTrue(retryDelay >= 1_000 && retryDelay < 5_000);
        assertEquals("Gateway submission failed.", event.getLastError());
        assertNull(event.getClaimToken());
        assertEquals(MessageStatus.QUEUED, message.getStatus());
        org.mockito.Mockito.verifyNoInteractions(statusHistoryService);
        org.mockito.Mockito.verifyNoInteractions(webhookEventGenerationService);
    }

    @Test
    void retryBackoffGrowsWithAttemptCount() {
        Tenant tenant = new Tenant("Acme");
        UUID tenantId = tenant.getId();
        Message message = new Message(tenant, "+14155552671", "Hello");
        OutboxEvent event = queuedEvent(message.getId(), tenantId);
        event.claim(Instant.now());
        event.claim(Instant.now());
        when(outboxEventRepository.lockById(event.getId())).thenReturn(Optional.of(event));
        when(messageRepository.findByIdAndTenant_Id(message.getId(), tenantId)).thenReturn(Optional.of(message));
        Instant before = Instant.now();

        transactionService.recordFailure(
                event.getId(), event.getClaimToken(), new OutboxMessageReference(message.getId(), tenantId),
                "Gateway submission failed.");

        long retryDelay = Duration.between(before, event.getAvailableAt()).toMillis();
        assertTrue(retryDelay >= 2_000 && retryDelay < 5_000);
        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
    }

    @Test
    void failureAtRetryLimitMarksEventAndMessageFailed() {
        Tenant tenant = new Tenant("Acme");
        UUID tenantId = tenant.getId();
        Message message = new Message(tenant, "+14155552671", "Hello");
        OutboxEvent event = queuedEvent(message.getId(), tenantId);
        event.claim(Instant.now());
        event.claim(Instant.now());
        event.claim(Instant.now());
        when(outboxEventRepository.lockById(event.getId())).thenReturn(Optional.of(event));
        when(messageRepository.findByIdAndTenant_Id(message.getId(), tenantId)).thenReturn(Optional.of(message));

        transactionService.recordFailure(
                event.getId(), event.getClaimToken(), new OutboxMessageReference(message.getId(), tenantId),
                "Gateway rejected submission.");

        assertEquals(OutboxEventStatus.FAILED, event.getStatus());
        assertEquals(MessageStatus.FAILED, message.getStatus());
        assertEquals("Gateway rejected submission.", event.getLastError());
        assertNull(event.getClaimToken());
        verify(messageRepository).save(message);
        verify(statusHistoryService).transition(message, MessageStatus.FAILED, "OUTBOX_DISPATCHER");
        verify(webhookEventGenerationService).createDeliveries(
                eq(tenantId), eq(message.getId()), eq(WebhookEventType.MESSAGE_FAILED),
                any(WebhookEventPayload.class));
    }

    @Test
    void expiredProcessingAtAttemptLimitIsTerminalizedDuringRecovery() {
        Tenant tenant = new Tenant("Acme");
        UUID tenantId = tenant.getId();
        Message message = new Message(tenant, "+14155552671", "Hello");
        OutboxEvent event = queuedEvent(message.getId(), tenantId);
        event.claim(Instant.now().minus(Duration.ofHours(1)));
        event.claim(Instant.now().minus(Duration.ofHours(1)));
        event.claim(Instant.now().minus(Duration.ofHours(1)));
        when(outboxEventRepository.lockDueOrAbandonedEvents(any(), any(), eq(properties.getBatchSize())))
                .thenReturn(List.of(event));
        when(outboxEventRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findByIdAndTenant_Id(message.getId(), tenantId)).thenReturn(Optional.of(message));

        List<OutboxEvent> claimed = transactionService.claimDueEvents();

        assertTrue(claimed.isEmpty());
        assertEquals(OutboxEventStatus.FAILED, event.getStatus());
        assertEquals(MessageStatus.FAILED, message.getStatus());
        verify(messageRepository).save(message);
        verify(statusHistoryService).transition(message, MessageStatus.FAILED, "OUTBOX_DISPATCHER");
        verify(webhookEventGenerationService).createDeliveries(
                eq(tenantId), eq(message.getId()), eq(WebhookEventType.MESSAGE_FAILED),
                any(WebhookEventPayload.class));
    }

    @Test
    void claimQueryUsesPostgresSkipLocked() throws Exception {
        Query query = OutboxEventRepository.class
                .getMethod("lockDueOrAbandonedEvents", Instant.class, Instant.class, int.class)
                .getAnnotation(Query.class);

        assertNotNull(query);
        assertTrue(query.value().contains("FOR UPDATE SKIP LOCKED"));
    }

    private OutboxEvent queuedEvent(UUID messageId, UUID tenantId) {
        return new OutboxEvent("MESSAGE_QUEUED", "MESSAGE", messageId,
                Map.of("messageId", messageId, "tenantId", tenantId));
    }
}
