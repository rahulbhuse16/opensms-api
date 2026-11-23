package com.opensms.apikey.controller;

import com.opensms.apikey.dto.ApiKeyResponse;
import com.opensms.apikey.dto.CreateApiKeyRequest;
import com.opensms.apikey.dto.CreatedApiKeyResponse;
import com.opensms.apikey.service.ApiKeyService;
import com.opensms.security.TenantPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    public ResponseEntity<CreatedApiKeyResponse> createApiKey(
            @AuthenticationPrincipal TenantPrincipal principal,
            @Valid @RequestBody CreateApiKeyRequest request
    ) {
        CreatedApiKeyResponse response = apiKeyService.createApiKey(principal, request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> getApiKeys(
            @AuthenticationPrincipal TenantPrincipal principal
    ) {
        return ResponseEntity.ok(apiKeyService.getApiKeys(principal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiKeyResponse> revokeApiKey(
            @AuthenticationPrincipal TenantPrincipal principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(apiKeyService.revokeApiKey(principal, id));
    }
}
