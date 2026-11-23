package com.opensms.outbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "opensms.outbox.dispatcher")
public class OutboxDispatcherProperties {

    private boolean enabled;
    private long pollIntervalMs = 5_000;
    private int batchSize = 20;
    private long claimTimeoutMs = 300_000;
    private int maxAttempts = 5;
    private long initialBackoffMs = 10_000;
    private long maxBackoffMs = 900_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getClaimTimeoutMs() {
        return claimTimeoutMs;
    }

    public void setClaimTimeoutMs(long claimTimeoutMs) {
        this.claimTimeoutMs = claimTimeoutMs;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getInitialBackoffMs() {
        return initialBackoffMs;
    }

    public void setInitialBackoffMs(long initialBackoffMs) {
        this.initialBackoffMs = initialBackoffMs;
    }

    public long getMaxBackoffMs() {
        return maxBackoffMs;
    }

    public void setMaxBackoffMs(long maxBackoffMs) {
        this.maxBackoffMs = maxBackoffMs;
    }

    public void validate() {
        if (pollIntervalMs < 1_000 || pollIntervalMs > 3_600_000) {
            throw new IllegalStateException("Outbox poll interval must be between 1000 ms and 3600000 ms.");
        }
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalStateException("Outbox batch size must be between 1 and 500.");
        }
        if (claimTimeoutMs < pollIntervalMs || claimTimeoutMs > 604_800_000) {
            throw new IllegalStateException("Outbox claim timeout must be at least the poll interval and at most 7 days.");
        }
        if (maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalStateException("Outbox maximum attempts must be between 1 and 100.");
        }
        if (initialBackoffMs < 1 || maxBackoffMs < initialBackoffMs || maxBackoffMs > 86_400_000) {
            throw new IllegalStateException("Outbox retry backoff settings are invalid.");
        }
    }
}
