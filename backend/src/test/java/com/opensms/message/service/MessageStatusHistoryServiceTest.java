package com.opensms.message.service;

import com.opensms.message.dto.MessageStatusHistoryResponse;
import com.opensms.message.entity.Message;
import com.opensms.message.entity.MessageStatus;
import com.opensms.message.entity.MessageStatusHistory;
import com.opensms.message.mapper.MessageStatusHistoryMapper;
import com.opensms.message.repository.MessageRepository;
import com.opensms.message.repository.MessageStatusHistoryRepository;
import com.opensms.tenant.entity.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageStatusHistoryServiceTest {

    @Mock
    private MessageStatusHistoryRepository historyRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MessageStatusHistoryMapper historyMapper;

    private MessageStatusHistoryService historyService;

    @BeforeEach
    void setUp() {
        historyService = new MessageStatusHistoryService(historyRepository, messageRepository, historyMapper);
    }

    @Test
    void recordsInitialQueuedStatusWithNullPreviousStatus() {
        Message message = new Message(new Tenant("Acme"), "+14155552671", "Hello");
        when(historyRepository.save(any(MessageStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        historyService.recordInitialQueued(message);

        ArgumentCaptor<MessageStatusHistory> captor = ArgumentCaptor.forClass(MessageStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        MessageStatusHistory history = captor.getValue();
        assertEquals(message.getId(), history.getMessage().getId());
        assertEquals(message.getTenant().getId(), history.getTenant().getId());
        assertNull(history.getPreviousStatus());
        assertEquals(MessageStatus.QUEUED, history.getNewStatus());
        assertEquals("API", history.getChangeSource());
    }

    @Test
    void recordsSimulatedSentAndFailedTransitions() {
        Message message = new Message(new Tenant("Acme"), "+14155552671", "Hello");
        when(historyRepository.save(any(MessageStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(historyService.transition(message, MessageStatus.SIMULATED, "OUTBOX_DISPATCHER"));
        assertTrue(historyService.transition(message, MessageStatus.SENT, "OUTBOX_DISPATCHER"));
        assertTrue(historyService.transition(message, MessageStatus.FAILED, "OUTBOX_DISPATCHER"));

        ArgumentCaptor<MessageStatusHistory> captor = ArgumentCaptor.forClass(MessageStatusHistory.class);
        verify(historyRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        List<MessageStatusHistory> history = captor.getAllValues();
        assertEquals(MessageStatus.QUEUED, history.get(0).getPreviousStatus());
        assertEquals(MessageStatus.SIMULATED, history.get(0).getNewStatus());
        assertEquals(MessageStatus.SIMULATED, history.get(1).getPreviousStatus());
        assertEquals(MessageStatus.SENT, history.get(1).getNewStatus());
        assertEquals(MessageStatus.SENT, history.get(2).getPreviousStatus());
        assertEquals(MessageStatus.FAILED, history.get(2).getNewStatus());
    }

    @Test
    void doesNotWriteDuplicateWhenStatusIsUnchanged() {
        Message message = new Message(new Tenant("Acme"), "+14155552671", "Hello");
        when(historyRepository.save(any(MessageStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        historyService.transition(message, MessageStatus.SIMULATED, "OUTBOX_DISPATCHER");

        boolean changed = historyService.transition(message, MessageStatus.SIMULATED, "OUTBOX_DISPATCHER");

        assertFalse(changed);
        verify(historyRepository).save(any(MessageStatusHistory.class));
    }

    @Test
    void historyReadVerifiesAndScopesByTenant() {
        Message message = new Message(new Tenant("Acme"), "+14155552671", "Hello");
        UUID tenantId = message.getTenant().getId();
        UUID messageId = message.getId();
        MessageStatusHistory entity = new MessageStatusHistory(message, null, MessageStatus.QUEUED, "API");
        MessageStatusHistoryResponse response = new MessageStatusHistoryResponse(
                entity.getId(), message.getId(), null, MessageStatus.QUEUED, "API", null);
        when(messageRepository.findByIdAndTenant_Id(messageId, tenantId)).thenReturn(Optional.of(message));
        when(historyRepository.findAllByMessage_IdAndTenant_IdOrderByCreatedAtAsc(messageId, tenantId))
                .thenReturn(List.of(entity));
        when(historyMapper.toResponse(entity)).thenReturn(response);

        List<MessageStatusHistoryResponse> result = historyService.getHistory(messageId, tenantId);

        assertEquals(List.of(response), result);
        verify(messageRepository).findByIdAndTenant_Id(messageId, tenantId);
        verify(historyRepository).findAllByMessage_IdAndTenant_IdOrderByCreatedAtAsc(messageId, tenantId);
        verify(historyMapper).toResponse(entity);
    }

    @Test
    void doesNotReadHistoryForMessageOutsideTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(messageRepository.findByIdAndTenant_Id(messageId, tenantId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> historyService.getHistory(messageId, tenantId));

        verify(messageRepository).findByIdAndTenant_Id(messageId, tenantId);
        verifyNoInteractions(historyRepository, historyMapper);
    }
}
