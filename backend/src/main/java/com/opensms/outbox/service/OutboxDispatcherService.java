package com.opensms.outbox.service;

import com.opensms.gateway.SmsGateway;
import com.opensms.gateway.SmsGatewayRequest;
import com.opensms.gateway.SmsGatewayResult;
import com.opensms.message.entity.Message;
import com.opensms.message.repository.MessageRepository;
import com.opensms.outbox.config.OutboxDispatcherProperties;
import com.opensms.outbox.entity.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OutboxDispatcherService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxDispatcherService.class);

    private final OutboxTransactionService transactionService;
    private final MessageRepository messageRepository;
    private final SmsGateway smsGateway;

    public OutboxDispatcherService(
            OutboxTransactionService transactionService,
            MessageRepository messageRepository,
            SmsGateway smsGateway,
            OutboxDispatcherProperties properties
    ) {
        this.transactionService = transactionService;
        this.messageRepository = messageRepository;
        this.smsGateway = smsGateway;
        properties.validate();
    }

    /**
     * Claims due events in a short transaction and processes them without a transaction around gateway calls.
     * Retries are bounded; a crash after gateway acceptance but before finalization may cause a duplicate submission.
     * Exactly-once gateway execution is not guaranteed.
     */
    public void dispatchBatch() {
        List<OutboxEvent> claimedEvents = transactionService.claimDueEvents();
        for (OutboxEvent event : claimedEvents) {
            try {
                process(event);
            } catch (RuntimeException exception) {
                // Event ID is safe to log; exception details may contain provider or message data.
                LOGGER.error("Outbox event {} could not be finalized; its claim will expire for recovery.",
                        event.getId());
            }
        }
    }

    private void process(OutboxEvent event) {
        OutboxMessageReference reference;
        try {
            reference = OutboxMessageReference.from(event);
        } catch (IllegalArgumentException exception) {
            transactionService.recordFailure(
                    event.getId(), event.getClaimToken(), null, "Invalid queued-message event payload.");
            return;
        }

        Optional<Message> optionalMessage;
        try {
            optionalMessage = messageRepository.findByIdAndTenant_Id(
                    reference.messageId(), reference.tenantId());
        } catch (RuntimeException exception) {
            transactionService.recordFailure(
                    event.getId(), event.getClaimToken(), reference, "Tenant-scoped message lookup failed.");
            return;
        }
        if (optionalMessage.isEmpty()) {
            transactionService.recordFailure(
                    event.getId(), event.getClaimToken(), reference, "Referenced tenant-owned message was not found.");
            return;
        }

        Message message = optionalMessage.get();
        SmsGatewayResult result;
        try {
            result = smsGateway.submit(new SmsGatewayRequest(
                    message.getId(), message.getDestinationPhoneNumber(), message.getBody()));
        } catch (RuntimeException exception) {
            transactionService.recordFailure(
                    event.getId(), event.getClaimToken(), reference, "Gateway submission failed.");
            return;
        }

        if (result == null) {
            transactionService.recordFailure(
                    event.getId(), event.getClaimToken(), reference, "Gateway returned no submission result.");
        } else if (!result.accepted()) {
            transactionService.recordFailure(
                    event.getId(), event.getClaimToken(), reference, "Gateway rejected submission.");
        } else {
            transactionService.recordAccepted(
                    event.getId(), event.getClaimToken(), reference, result.simulated());
        }
    }
}
