package com.opensms.webhook.service;

import com.opensms.message.entity.Message;
import com.opensms.message.entity.MessageStatus;
import com.opensms.message.repository.MessageRepository;
import com.opensms.tenant.entity.Tenant;
import com.opensms.webhook.dto.WebhookEventPayload;
import com.opensms.webhook.entity.WebhookDelivery;
import com.opensms.webhook.entity.WebhookDeliveryStatus;
import com.opensms.webhook.entity.WebhookEventType;
import com.opensms.webhook.entity.WebhookSubscription;
import com.opensms.webhook.entity.WebhookSubscriptionStatus;
import com.opensms.webhook.repository.WebhookDeliveryRepository;
import com.opensms.webhook.repository.WebhookSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookEventGenerationServiceTest {

    @Mock private WebhookSubscriptionRepository subscriptionRepository;
    @Mock private WebhookDeliveryRepository deliveryRepository;
    @Mock private MessageRepository messageRepository;

    private WebhookEventGenerationService service;

    @BeforeEach
    void setUp() {
        service = new WebhookEventGenerationService(subscriptionRepository, deliveryRepository, messageRepository);
    }

    @Test
    void matchingActiveSubscriptionReceivesDeliveryWithExactSecretSnapshots() {
        Tenant tenant = new Tenant("Acme");
        Message message = message(tenant, MessageStatus.QUEUED);
        byte[] ciphertext = new byte[]{9, 7, 5, 3};
        byte[] iv = new byte[12];
        iv[0] = 42;
        WebhookSubscription subscription = subscription(tenant, WebhookSubscriptionStatus.ACTIVE,
                List.of(WebhookEventType.MESSAGE_QUEUED), "https://hooks.example.test/old", ciphertext, iv);
        prepare(message, List.of(subscription));
        WebhookEventPayload payload = WebhookEventPayload.fromMessage(message, WebhookEventType.MESSAGE_QUEUED);

        int created = service.createDeliveries(tenant.getId(), message.getId(), WebhookEventType.MESSAGE_QUEUED, payload);

        assertEquals(1, created);
        ArgumentCaptor<List<WebhookDelivery>> captor = ArgumentCaptor.forClass(List.class);
        verify(deliveryRepository).saveAll(captor.capture());
        WebhookDelivery delivery = captor.getValue().get(0);
        assertEquals(tenant.getId(), delivery.getTenant().getId());
        assertEquals(subscription.getId(), delivery.getSubscriptionIdSnapshot());
        assertEquals("https://hooks.example.test/old", delivery.getDestinationUrlSnapshot());
        assertArrayEquals(ciphertext, delivery.getEncryptedSigningSecretSnapshot());
        assertArrayEquals(iv, delivery.getSigningSecretIvSnapshot());
        assertEquals(payload.eventId(), delivery.getEventId());
        assertEquals(payload, delivery.getEventPayload());
        assertEquals(WebhookDeliveryStatus.PENDING, delivery.getStatus());
        assertEquals(0, delivery.getAttemptCount());
    }

    @Test
    void filtersUnsubscribedInactiveAndOtherTenantSubscriptions() {
        Tenant tenant = new Tenant("Acme");
        Tenant otherTenant = new Tenant("Other");
        Message message = message(tenant, MessageStatus.SENT);
        WebhookSubscription notSubscribed = subscription(tenant, WebhookSubscriptionStatus.ACTIVE,
                List.of(WebhookEventType.MESSAGE_QUEUED), "https://hooks.example.test/a", new byte[]{1}, new byte[12]);
        WebhookSubscription inactive = subscription(tenant, WebhookSubscriptionStatus.SUSPENDED,
                List.of(WebhookEventType.MESSAGE_SENT), "https://hooks.example.test/b", new byte[]{2}, new byte[12]);
        WebhookSubscription otherTenantSubscription = subscription(otherTenant, WebhookSubscriptionStatus.ACTIVE,
                List.of(WebhookEventType.MESSAGE_SENT), "https://hooks.example.test/c", new byte[]{3}, new byte[12]);
        prepare(message, List.of(notSubscribed, inactive, otherTenantSubscription));

        int created = service.createDeliveries(tenant.getId(), message.getId(), WebhookEventType.MESSAGE_SENT,
                WebhookEventPayload.fromMessage(message, WebhookEventType.MESSAGE_SENT));

        assertEquals(0, created);
        verify(deliveryRepository, never()).saveAll(any());
    }

    @Test
    void createsIndependentDeliveryForEachMatchingSubscriptionWithSharedEventId() {
        Tenant tenant = new Tenant("Acme");
        Message message = message(tenant, MessageStatus.SIMULATED);
        WebhookSubscription first = subscription(tenant, WebhookSubscriptionStatus.ACTIVE,
                List.of(WebhookEventType.MESSAGE_SIMULATED), "https://hooks.example.test/first", new byte[]{1}, new byte[12]);
        WebhookSubscription second = subscription(tenant, WebhookSubscriptionStatus.ACTIVE,
                List.of(WebhookEventType.MESSAGE_SIMULATED), "https://hooks.example.test/second", new byte[]{2}, new byte[12]);
        prepare(message, List.of(first, second));
        WebhookEventPayload payload = WebhookEventPayload.fromMessage(message, WebhookEventType.MESSAGE_SIMULATED);

        assertEquals(2, service.createDeliveries(tenant.getId(), message.getId(),
                WebhookEventType.MESSAGE_SIMULATED, payload));

        ArgumentCaptor<List<WebhookDelivery>> captor = ArgumentCaptor.forClass(List.class);
        verify(deliveryRepository).saveAll(captor.capture());
        List<WebhookDelivery> deliveries = captor.getValue();
        assertEquals(2, deliveries.size());
        assertEquals(payload.eventId(), deliveries.get(0).getEventId());
        assertEquals(payload.eventId(), deliveries.get(1).getEventId());
        assertEquals(first.getId(), deliveries.get(0).getSubscriptionIdSnapshot());
        assertEquals(second.getId(), deliveries.get(1).getSubscriptionIdSnapshot());
    }

    @Test
    void noMatchingSubscriptionsCompletesWithoutPersistence() {
        Tenant tenant = new Tenant("Acme");
        Message message = message(tenant, MessageStatus.FAILED);
        prepare(message, List.of());

        assertEquals(0, service.createDeliveries(tenant.getId(), message.getId(), WebhookEventType.MESSAGE_FAILED,
                WebhookEventPayload.fromMessage(message, WebhookEventType.MESSAGE_FAILED)));
        verify(deliveryRepository, never()).saveAll(any());
    }

    @Test
    void duplicateEventInvocationIsSkippedByStableEventAndSubscriptionIdentity() {
        Tenant tenant = new Tenant("Acme");
        Message message = message(tenant, MessageStatus.QUEUED);
        WebhookSubscription subscription = subscription(tenant, WebhookSubscriptionStatus.ACTIVE,
                List.of(WebhookEventType.MESSAGE_QUEUED), "https://hooks.example.test/events", new byte[]{1}, new byte[12]);
        prepare(message, List.of(subscription));
        WebhookEventPayload payload = WebhookEventPayload.fromMessage(message, WebhookEventType.MESSAGE_QUEUED);
        when(deliveryRepository.existsByEventIdAndSubscriptionIdSnapshotAndTenant_Id(
                payload.eventId(), subscription.getId(), tenant.getId()))
                .thenReturn(false, true);

        assertEquals(1, service.createDeliveries(tenant.getId(), message.getId(), WebhookEventType.MESSAGE_QUEUED, payload));
        assertEquals(0, service.createDeliveries(tenant.getId(), message.getId(), WebhookEventType.MESSAGE_QUEUED, payload));
        verify(deliveryRepository, org.mockito.Mockito.times(1)).saveAll(any());
    }

    @Test
    void saveFailurePropagatesToRollBackTheCallerTransaction() {
        Tenant tenant = new Tenant("Acme");
        Message message = message(tenant, MessageStatus.FAILED);
        WebhookSubscription subscription = subscription(tenant, WebhookSubscriptionStatus.ACTIVE,
                List.of(WebhookEventType.MESSAGE_FAILED), "https://hooks.example.test/events", new byte[]{1}, new byte[12]);
        prepare(message, List.of(subscription));
        when(deliveryRepository.saveAll(any())).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> service.createDeliveries(
                tenant.getId(), message.getId(), WebhookEventType.MESSAGE_FAILED,
                WebhookEventPayload.fromMessage(message, WebhookEventType.MESSAGE_FAILED)));
    }

    private void prepare(Message message, List<WebhookSubscription> subscriptions) {
        when(messageRepository.findByIdAndTenant_Id(message.getId(), message.getTenant().getId()))
                .thenReturn(Optional.of(message));
        when(subscriptionRepository.findAllByTenant_IdAndStatus(
                message.getTenant().getId(), WebhookSubscriptionStatus.ACTIVE)).thenReturn(subscriptions);
        when(deliveryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Message message(Tenant tenant, MessageStatus status) {
        Message message = new Message(tenant, "+14155552671", "Hello");
        if (status != MessageStatus.QUEUED) message.changeStatus(status);
        return message;
    }

    private WebhookSubscription subscription(Tenant tenant, WebhookSubscriptionStatus status,
                                              List<WebhookEventType> eventTypes, String url,
                                              byte[] ciphertext, byte[] iv) {
        WebhookSubscription subscription = new WebhookSubscription(tenant, url, eventTypes, ciphertext, iv);
        if (status == WebhookSubscriptionStatus.SUSPENDED) {
            subscription.updateConfiguration(url, eventTypes, status);
        }
        return subscription;
    }
}
