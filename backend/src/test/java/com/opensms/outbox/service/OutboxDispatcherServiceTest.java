package com.opensms.outbox.service;

import com.opensms.gateway.SmsGateway;
import com.opensms.gateway.SmsGatewayRequest;
import com.opensms.gateway.SmsGatewayResult;
import com.opensms.message.entity.Message;
import com.opensms.message.repository.MessageRepository;
import com.opensms.outbox.config.OutboxDispatcherProperties;
import com.opensms.outbox.entity.OutboxEvent;
import com.opensms.tenant.entity.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxDispatcherServiceTest {

    @Mock
    private OutboxTransactionService transactionService;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private SmsGateway smsGateway;

    private OutboxDispatcherService dispatcherService;

    @BeforeEach
    void setUp() {
        dispatcherService = new OutboxDispatcherService(
                transactionService, messageRepository, smsGateway, new OutboxDispatcherProperties());
    }

    @Test
    void claimedPendingEventLoadsTenantMessageAndSubmitsGatewayRequest() {
        UUID tenantId = UUID.randomUUID();
        Message message = new Message(new Tenant("Acme"), "+14155552671", "Hello");
        OutboxEvent event = queuedEvent(message.getId(), tenantId);
        event.claim(Instant.now());
        when(transactionService.claimDueEvents()).thenReturn(List.of(event));
        when(messageRepository.findByIdAndTenant_Id(message.getId(), tenantId)).thenReturn(Optional.of(message));
        when(smsGateway.submit(any(SmsGatewayRequest.class)))
                .thenReturn(new SmsGatewayResult(true, "mock-" + message.getId(), "simulated", true));

        dispatcherService.dispatchBatch();

        verify(messageRepository).findByIdAndTenant_Id(message.getId(), tenantId);
        verify(smsGateway).submit(new SmsGatewayRequest(message.getId(), "+14155552671", "Hello"));
        verify(transactionService).recordAccepted(
                event.getId(), event.getClaimToken(), new OutboxMessageReference(message.getId(), tenantId), true);
    }

    @Test
    void rejectedGatewayResultIsRecordedAsSanitizedFailure() {
        UUID tenantId = UUID.randomUUID();
        Message message = new Message(new Tenant("Acme"), "+14155552671", "Hello");
        OutboxEvent event = queuedEvent(message.getId(), tenantId);
        event.claim(Instant.now());
        when(transactionService.claimDueEvents()).thenReturn(List.of(event));
        when(messageRepository.findByIdAndTenant_Id(message.getId(), tenantId)).thenReturn(Optional.of(message));
        when(smsGateway.submit(any(SmsGatewayRequest.class)))
                .thenReturn(new SmsGatewayResult(false, null, "contains private provider detail", false));

        dispatcherService.dispatchBatch();

        verify(transactionService).recordFailure(
                event.getId(), event.getClaimToken(), new OutboxMessageReference(message.getId(), tenantId),
                "Gateway rejected submission.");
    }

    @Test
    void gatewayExceptionIsRecordedWithoutPersistingExceptionDetails() {
        UUID tenantId = UUID.randomUUID();
        Message message = new Message(new Tenant("Acme"), "+14155552671", "Sensitive SMS body");
        OutboxEvent event = queuedEvent(message.getId(), tenantId);
        event.claim(Instant.now());
        when(transactionService.claimDueEvents()).thenReturn(List.of(event));
        when(messageRepository.findByIdAndTenant_Id(message.getId(), tenantId)).thenReturn(Optional.of(message));
        when(smsGateway.submit(any(SmsGatewayRequest.class)))
                .thenThrow(new IllegalStateException("Sensitive SMS body and destination detail"));

        dispatcherService.dispatchBatch();

        verify(transactionService).recordFailure(
                event.getId(), event.getClaimToken(), new OutboxMessageReference(message.getId(), tenantId),
                "Gateway submission failed.");
    }

    @Test
    void malformedPayloadFailsSafelyWithoutLoadingOrSubmittingMessage() {
        UUID messageId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(
                "MESSAGE_QUEUED", "MESSAGE", messageId, Map.of("messageId", messageId));
        event.claim(Instant.now());
        when(transactionService.claimDueEvents()).thenReturn(List.of(event));

        dispatcherService.dispatchBatch();

        verify(transactionService).recordFailure(
                event.getId(), event.getClaimToken(), null, "Invalid queued-message event payload.");
        verifyNoInteractions(messageRepository, smsGateway);
    }

    @Test
    void messageOwnedByAnotherTenantIsNotLoadedOrSubmitted() {
        UUID tenantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        OutboxEvent event = queuedEvent(messageId, tenantId);
        event.claim(Instant.now());
        when(transactionService.claimDueEvents()).thenReturn(List.of(event));
        when(messageRepository.findByIdAndTenant_Id(messageId, tenantId)).thenReturn(Optional.empty());

        dispatcherService.dispatchBatch();

        verify(messageRepository).findByIdAndTenant_Id(messageId, tenantId);
        verify(transactionService).recordFailure(
                event.getId(), event.getClaimToken(), new OutboxMessageReference(messageId, tenantId),
                "Referenced tenant-owned message was not found.");
        verifyNoInteractions(smsGateway);
    }

    private OutboxEvent queuedEvent(UUID messageId, UUID tenantId) {
        return new OutboxEvent("MESSAGE_QUEUED", "MESSAGE", messageId,
                Map.of("messageId", messageId, "tenantId", tenantId));
    }
}
