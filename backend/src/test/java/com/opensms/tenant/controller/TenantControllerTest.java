package com.opensms.tenant.controller;

import com.opensms.security.ApiKeyAuthenticationService;
import com.opensms.security.SecurityConfig;
import com.opensms.security.TenantPrincipal;
import com.opensms.tenant.dto.TenantResponse;
import com.opensms.tenant.entity.TenantStatus;
import com.opensms.tenant.service.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenantController.class)
@Import(SecurityConfig.class)
class TenantControllerTest {

    private static final String API_KEY = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TenantService tenantService;

    @MockitoBean
    private ApiKeyAuthenticationService authenticationService;

    @Test
    void authenticatedTenantCanRetrieveItsOwnDetails() throws Exception {
        TenantPrincipal principal = new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID());
        TenantResponse response = tenantResponse("Acme", principal.tenantId());
        authenticateAs(principal);
        when(tenantService.getTenantById(principal.tenantId())).thenReturn(response);

        mockMvc.perform(authenticated(get("/api/v1/tenants/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(principal.tenantId().toString()))
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").value(response.createdAt().toString()))
                .andExpect(jsonPath("$.updatedAt").value(response.updatedAt().toString()));

        verify(tenantService).getTenantById(principal.tenantId());
    }

    @Test
    void unauthenticatedTenantRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/v1/tenants/me"));

        verifyNoInteractions(tenantService);
    }

    @Test
    void missingAuthenticatedTenantReturnsStandard404() throws Exception {
        TenantPrincipal principal = new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID());
        authenticateAs(principal);
        when(tenantService.getTenantById(principal.tenantId()))
                .thenThrow(new NoSuchElementException("Tenant not found: " + principal.tenantId()));

        mockMvc.perform(authenticated(get("/api/v1/tenants/me")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/v1/tenants/me"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void arbitraryTenantRetrievalAndListingAreNotExposed() throws Exception {
        TenantPrincipal principal = new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID());
        authenticateAs(principal);

        mockMvc.perform(authenticated(get("/api/v1/tenants/{id}", UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mockMvc.perform(authenticated(get("/api/v1/tenants")))
                .andExpect(status().isNotFound());

        verifyNoInteractions(tenantService);
    }

    @Test
    void tenantCreationIsNotExposedAsPublicEndpoint() throws Exception {
        TenantPrincipal principal = new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID());
        authenticateAs(principal);

        mockMvc.perform(authenticated(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\"}")))
                .andExpect(status().isNotFound());

        verifyNoInteractions(tenantService);
    }

    private void authenticateAs(TenantPrincipal principal) {
        when(authenticationService.authenticate(API_KEY)).thenReturn(principal);
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.header("X-API-Key", API_KEY);
    }

    private TenantResponse tenantResponse(String name, UUID id) {
        Instant timestamp = Instant.parse("2025-01-01T00:00:00Z");
        return new TenantResponse(id, name, TenantStatus.ACTIVE, timestamp, timestamp);
    }
}
