package com.opensms.analytics.controller;

import com.opensms.analytics.dto.DailyMessageVolumeResponse;
import com.opensms.analytics.dto.MessageAnalyticsSummaryResponse;
import com.opensms.analytics.service.MessageAnalyticsService;
import com.opensms.common.exception.GlobalExceptionHandler;
import com.opensms.message.entity.MessageStatus;
import com.opensms.security.ApiKeyAuthenticationService;
import com.opensms.security.SecurityConfig;
import com.opensms.security.TenantPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AnalyticsControllerTest {

    private static final String API_KEY = "analytics-test-key";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MessageAnalyticsService analyticsService;
    @MockitoBean private ApiKeyAuthenticationService authenticationService;

    @Test
    void summaryEndpointRequiresApiKeyAndUsesOptionalDateParameters() throws Exception {
        TenantPrincipal principal = principal();
        authenticate(principal);
        LocalDate from = LocalDate.of(2025, 4, 1);
        LocalDate to = LocalDate.of(2025, 4, 30);
        EnumMap<MessageStatus, Long> counts = zeroCounts();
        counts.put(MessageStatus.SENT, 3L);
        when(analyticsService.summary(principal, from, to)).thenReturn(
                new MessageAnalyticsSummaryResponse(8, counts, from, to, 3, counts));

        mockMvc.perform(authenticated(get("/api/v1/analytics/summary")
                        .queryParam("from", from.toString()).queryParam("to", to.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMessages").value(8))
                .andExpect(jsonPath("$.messagesByStatus.SENT").value(3))
                .andExpect(jsonPath("$.messagesByStatus.DELIVERED").value(0))
                .andExpect(jsonPath("$.periodTotalMessages").value(3))
                .andExpect(jsonPath("$.periodStart").value(from.toString()))
                .andExpect(jsonPath("$.periodEnd").value(to.toString()));

        verify(analyticsService).summary(principal, from, to);
    }

    @Test
    void dailyEndpointReturnsChronologicalDaysAndPassesTenantPrincipal() throws Exception {
        TenantPrincipal principal = principal();
        authenticate(principal);
        LocalDate from = LocalDate.of(2025, 5, 1);
        LocalDate to = LocalDate.of(2025, 5, 2);
        when(analyticsService.dailyVolume(principal, from, to)).thenReturn(new DailyMessageVolumeResponse(
                from, to, List.of(
                new DailyMessageVolumeResponse.Day(from, 2, statusCounts(MessageStatus.QUEUED, 2)),
                new DailyMessageVolumeResponse.Day(to, 0, zeroCounts()))));

        mockMvc.perform(authenticated(get("/api/v1/analytics/messages/daily")
                        .queryParam("from", from.toString()).queryParam("to", to.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[0].date").value(from.toString()))
                .andExpect(jsonPath("$.days[0].messagesByStatus.QUEUED").value(2))
                .andExpect(jsonPath("$.days[1].totalMessages").value(0));

        verify(analyticsService).dailyVolume(principal, from, to);
    }

    @Test
    void unauthenticatedAndMalformedDateRequestsReturn400Or401() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(analyticsService);

        TenantPrincipal principal = principal();
        authenticate(principal);
        mockMvc.perform(authenticated(get("/api/v1/analytics/summary").queryParam("from", "not-a-date")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(analyticsService);
    }

    private EnumMap<MessageStatus, Long> zeroCounts() {
        EnumMap<MessageStatus, Long> counts = new EnumMap<>(MessageStatus.class);
        for (MessageStatus status : MessageStatus.values()) counts.put(status, 0L);
        return counts;
    }

    private Map<MessageStatus, Long> statusCounts(MessageStatus status, long count) {
        EnumMap<MessageStatus, Long> counts = zeroCounts();
        counts.put(status, count);
        return counts;
    }

    private TenantPrincipal principal() {
        return new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID());
    }

    private void authenticate(TenantPrincipal principal) {
        when(authenticationService.authenticate(API_KEY)).thenReturn(principal);
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.header("X-API-Key", API_KEY);
    }
}
