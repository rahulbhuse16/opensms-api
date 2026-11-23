package com.opensms.outbox.scheduler;

import com.opensms.outbox.service.OutboxDispatcherService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "opensms.outbox.dispatcher", name = "enabled", havingValue = "true")
public class OutboxDispatcherScheduler {

    private final OutboxDispatcherService dispatcherService;

    public OutboxDispatcherScheduler(OutboxDispatcherService dispatcherService) {
        this.dispatcherService = dispatcherService;
    }

    @Scheduled(fixedDelayString = "${opensms.outbox.dispatcher.poll-interval-ms:5000}")
    public void dispatchBatch() {
        dispatcherService.dispatchBatch();
    }
}
