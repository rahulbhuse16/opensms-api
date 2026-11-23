package com.opensms.analytics.controller;

import com.opensms.analytics.dto.DailyMessageVolumeResponse;
import com.opensms.analytics.dto.MessageAnalyticsSummaryResponse;
import com.opensms.analytics.service.MessageAnalyticsService;
import com.opensms.security.TenantPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final MessageAnalyticsService analyticsService;

    public AnalyticsController(MessageAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public ResponseEntity<MessageAnalyticsSummaryResponse> summary(
            @AuthenticationPrincipal TenantPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(analyticsService.summary(principal, from, to));
    }

    @GetMapping("/messages/daily")
    public ResponseEntity<DailyMessageVolumeResponse> dailyVolume(
            @AuthenticationPrincipal TenantPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(analyticsService.dailyVolume(principal, from, to));
    }
}
