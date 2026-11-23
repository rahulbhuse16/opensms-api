package com.opensms.analytics.repository;

import java.time.LocalDate;

public interface DailyStatusCountProjection {
    LocalDate getDay();

    String getStatus();

    Long getMessageCount();
}
