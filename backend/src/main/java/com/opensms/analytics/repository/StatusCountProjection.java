package com.opensms.analytics.repository;

public interface StatusCountProjection {
    String getStatus();

    Long getMessageCount();
}
