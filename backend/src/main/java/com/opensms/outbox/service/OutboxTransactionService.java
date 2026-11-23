package com.opensms.outbox.service;

import com.opensms.message.entity.Message;
import com.opensms.message.entity.MessageStatus;
import com.opensms.message.repository.MessageRepository;
import com.opensms.message.service.MessageStatusHistoryService;
import com.opensms.webhook.dto.WebhookEventPayload;
import com.opensms.webhook.entity.WebhookEventType;
import com.opensms.webhook.service.WebhookEventGenerationService;
import com.opensms.outbox.config.OutboxDispatcherProperties;
import com.opensms.outbox.entity.OutboxEvent;
import com.opensms.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OutboxTransactionService {

    private static final String MAX_ATTEMPTS_ERROR = "Maximum outbox processing attempts reached.";

    private final OutboxEventRepository outboxEventRepository;
    private final MessageRepository messageRepository;
    private final MessageStatusHistoryService statusHistoryService;
    private final WebhookEventGenerationService webhookEventGenerationService;
    private final OutboxDispatcherProperties properties;

    public OutboxTransactionService(
            OutboxEventRepository outboxEventRepository,
            MessageRepository messageRepository,
            MessageStatusHistoryService statusHistoryService,
            WebhookEventGenerationService webhookEventGenerationService,
            OutboxDispatcherProperties properties
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.messageRepository = messageRepository;
        this.statusHistoryService = statusHistoryService;
        this.webhookEventGenerationService = webhookEventGenerationService;
        this.properties = properties;
        properties.validate();
    }

    @Transactional
    public List<OutboxEvent> claimDueEvents() {
        Instant now = Instant.now();
        Instant staleBefore = now.minusMillis(properties.getClaimTimeoutMs());
        List<OutboxEvent> candidates = outboxEventRepository.lockDueOrAbandonedEvents(
                now, staleBefore, properties.getBatchSize());
        List<OutboxEvent> claimed = new ArrayList<>();

        for (OutboxEvent event : candidates) {
            if (event.getAttemptCount() >= properties.getMaxAttempts()) {
                event.markTerminalFailure(MAX_ATTEMPTS_ERROR);
                markReferencedMessageFailed(event);
            } else {
                event.claim(now);
                claimed.add(event);
            }
        }
        outboxEventRepository.saveAll(candidates);
        return claimed;
    }

    @Transactional
    public void recordAccepted(
            UUID eventId,
            UUID claimToken,
            OutboxMessageReference reference,
            boolean simulated
    ) {
        Optional<OutboxEvent> optionalEvent = outboxEventRepository.lockById(eventId);
        if (optionalEvent.isEmpty() || !optionalEvent.get().isClaimedBy(claimToken)) {
            return;
        }
        OutboxEvent event = optionalEvent.get();
        Message message = messageRepository.findByIdAndTenant_Id(reference.messageId(), reference.tenantId())
                .orElseThrow(() -> new IllegalStateException("Referenced tenant-owned message no longer exists."));
        MessageStatus newStatus = simulated ? MessageStatus.SIMULATED : MessageStatus.SENT;
        if (statusHistoryService.transition(message, newStatus, "OUTBOX_DISPATCHER")) {
            WebhookEventType eventType = simulated ? WebhookEventType.MESSAGE_SIMULATED : WebhookEventType.MESSAGE_SENT;
            createWebhookEvent(message, eventType);
        }
        event.markPublished(claimToken, Instant.now());
        messageRepository.save(message);
        outboxEventRepository.save(event);
    }

    @Transactional
    public void recordFailure(
            UUID eventId,
            UUID claimToken,
            OutboxMessageReference reference,
            String sanitizedFailure
    ) {
        Optional<OutboxEvent> optionalEvent = outboxEventRepository.lockById(eventId);
        if (optionalEvent.isEmpty() || !optionalEvent.get().isClaimedBy(claimToken)) {
            return;
        }
        OutboxEvent event = optionalEvent.get();
        Optional<Message> message = findReferencedMessage(reference);
        if (event.getAttemptCount() >= properties.getMaxAttempts()) {
            event.markFailed(claimToken, sanitizedFailure);
            message.ifPresent(this::transitionToFailed);
            message.ifPresent(messageRepository::save);
        } else {
            event.scheduleRetry(claimToken, nextAttemptAt(event.getAttemptCount()), sanitizedFailure);
        }
        outboxEventRepository.save(event);
    }

    private Instant nextAttemptAt(int attemptCount) {
        long delay = properties.getInitialBackoffMs();
        for (int i = 1; i < attemptCount && delay < properties.getMaxBackoffMs(); i++) {
            delay = Math.min(delay * 2, properties.getMaxBackoffMs());
        }
        return Instant.now().plusMillis(delay);
    }

    private Optional<Message> findReferencedMessage(OutboxMessageReference reference) {
        if (reference == null) {
            return Optional.empty();
        }
        return messageRepository.findByIdAndTenant_Id(reference.messageId(), reference.tenantId());
    }

    private void markReferencedMessageFailed(OutboxEvent event) {
        try {
            OutboxMessageReference reference = OutboxMessageReference.from(event);
            messageRepository.findByIdAndTenant_Id(reference.messageId(), reference.tenantId())
                    .ifPresent(message -> {
                        transitionToFailed(message);
                        messageRepository.save(message);
                    });
        } catch (IllegalArgumentException ignored) {
            // An invalid payload cannot be used to safely identify a tenant-owned message.
        }
    }

    private void transitionToFailed(Message message) {
        if (statusHistoryService.transition(message, MessageStatus.FAILED, "OUTBOX_DISPATCHER")) {
            createWebhookEvent(message, WebhookEventType.MESSAGE_FAILED);
        }
    }

    private void createWebhookEvent(Message message, WebhookEventType eventType) {
        WebhookEventPayload payload = WebhookEventPayload.fromMessage(message, eventType);
        webhookEventGenerationService.createDeliveries(
                message.getTenant().getId(), message.getId(), eventType, payload);
    }
}
