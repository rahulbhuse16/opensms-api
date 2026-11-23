package com.opensms.tenant.controller;

import com.opensms.security.TenantPrincipal;
import com.opensms.tenant.dto.TenantResponse;
import com.opensms.tenant.service.TenantService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping("/me")
    public ResponseEntity<TenantResponse> getCurrentTenant(
            @AuthenticationPrincipal TenantPrincipal principal
    ) {
        return ResponseEntity.ok(tenantService.getTenantById(principal.tenantId()));
    }
}
