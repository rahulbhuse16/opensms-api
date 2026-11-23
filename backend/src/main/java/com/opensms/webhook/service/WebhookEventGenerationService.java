package com.opensms.webhook.service;

import com.opensms.message.entity.Message;
import com.opensms.message.entity.MessageStatus;
import com.opensms.message.repository.MessageRepository;
import com.opensms.webhook.dto.WebhookEventPayload;
import com.opensms.webhook.entity.WebhookDelivery;
import com.opensms.webhook.entity.WebhookEventType;
import com.opensms.webhook.entity.WebhookSubscription;
import com.opensms.webhook.entity.WebhookSubscriptionStatus;
import com.opensms.webhook.repository.WebhookDeliveryRepository;
import com.opensms.webhook.repository.WebhookSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class WebhookEventGenerationService {

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final MessageRepository messageRepository;

    public WebhookEventGenerationService(
            WebhookSubscriptionRepository subscriptionRepository,
            WebhookDeliveryRepository deliveryRepository,
            MessageRepository messageRepository
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int createDeliveries(UUID tenantId, UUID messageId, WebhookEventType eventType,
                                WebhookEventPayload payload) {
        if (tenantId == null || messageId == null || eventType == null || payload == null) {
            throw new IllegalArgumentException("Tenant, message, event type, and payload are required.");
        }
        if (!tenantId.equals(payload.tenantId()) || !messageId.equals(payload.messageId())
                || eventType != payload.eventType()
                || !WebhookEventPayload.stableEventId(messageId, eventType).equals(payload.eventId())) {
            throw new IllegalArgumentException("Webhook event payload does not match its message event.");
        }

        Message message = messageRepository.findByIdAndTenant_Id(messageId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Message not found for webhook event: " + messageId));
        if (!message.getDestinationPhoneNumber().equals(payload.destinationPhoneNumber())) {
            throw new IllegalArgumentException("Webhook event payload does not match its message.");
        }
        validatePayloadStatus(eventType, message.getStatus(), payload.messageStatus());

        List<WebhookDelivery> deliveries = new ArrayList<>();
        for (WebhookSubscription subscription : subscriptionRepository.findAllByTenant_IdAndStatus(
                tenantId, WebhookSubscriptionStatus.ACTIVE)) {
            if (subscription.getStatus() != WebhookSubscriptionStatus.ACTIVE
                    || !subscription.getTenant().getId().equals(tenantId)
                    || !subscription.getEventTypes().contains(eventType)
                    || deliveryRepository.existsByEventIdAndSubscriptionIdSnapshotAndTenant_Id(
                            payload.eventId(), subscription.getId(), tenantId)) {
                continue;
            }
            deliveries.add(new WebhookDelivery(subscription, message, payload));
        }
        if (deliveries.isEmpty()) {
            return 0;
        }
        deliveryRepository.saveAll(deliveries);
        return deliveries.size();
    }

    private void validatePayloadStatus(WebhookEventType eventType, MessageStatus actual, MessageStatus payloadStatus) {
        MessageStatus expected = switch (eventType) {
            case MESSAGE_QUEUED -> MessageStatus.QUEUED;
            case MESSAGE_SIMULATED -> MessageStatus.SIMULATED;
            case MESSAGE_SENT -> MessageStatus.SENT;
            case MESSAGE_FAILED -> MessageStatus.FAILED;
        };
        if (actual != expected || payloadStatus != expected) {
            throw new IllegalArgumentException("Webhook event type does not match the current message status.");
        }
    }
}
