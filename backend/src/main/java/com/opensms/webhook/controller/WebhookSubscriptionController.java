package com.opensms.webhook.controller;

import com.opensms.security.TenantPrincipal;
import com.opensms.webhook.dto.CreateWebhookSubscriptionRequest;
import com.opensms.webhook.dto.CreatedWebhookSubscriptionResponse;
import com.opensms.webhook.dto.UpdateWebhookSubscriptionRequest;
import com.opensms.webhook.dto.WebhookSubscriptionResponse;
import com.opensms.webhook.service.WebhookSubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookSubscriptionController {

    private final WebhookSubscriptionService subscriptionService;

    public WebhookSubscriptionController(WebhookSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    public ResponseEntity<CreatedWebhookSubscriptionResponse> create(
            @Valid @RequestBody CreateWebhookSubscriptionRequest request,
            @AuthenticationPrincipal TenantPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriptionService.create(principal, request));
    }

    @GetMapping
    public ResponseEntity<List<WebhookSubscriptionResponse>> list(
            @AuthenticationPrincipal TenantPrincipal principal
    ) {
        return ResponseEntity.ok(subscriptionService.list(principal));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WebhookSubscriptionResponse> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal TenantPrincipal principal
    ) {
        return ResponseEntity.ok(subscriptionService.get(principal, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WebhookSubscriptionResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWebhookSubscriptionRequest request,
            @AuthenticationPrincipal TenantPrincipal principal
    ) {
        return ResponseEntity.ok(subscriptionService.update(principal, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal TenantPrincipal principal
    ) {
        subscriptionService.delete(principal, id);
        return ResponseEntity.noContent().build();
    }
}
