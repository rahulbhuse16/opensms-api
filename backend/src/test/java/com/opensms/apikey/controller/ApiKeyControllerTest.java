package com.opensms.apikey.controller;

import com.opensms.apikey.dto.ApiKeyResponse;
import com.opensms.apikey.dto.CreatedApiKeyResponse;
import com.opensms.apikey.entity.ApiKeyStatus;
import com.opensms.apikey.service.ApiKeyService;
import com.opensms.security.ApiKeyAuthenticationService;
import com.opensms.security.SecurityConfig;
import com.opensms.security.TenantPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiKeyController.class)
@Import(SecurityConfig.class)
class ApiKeyControllerTest {

    private static final String API_KEY = "test-tenant-api-key";
    private static final String PLAINTEXT_CREATED_KEY = "os_live_once_only_secret";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyService apiKeyService;

    @MockitoBean
    private ApiKeyAuthenticationService authenticationService;

    @Test
    void authenticatedCreationReturns201AndPlaintextOnlyOnce() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);
        Instant createdAt = Instant.parse("2025-01-01T00:00:00Z");
        CreatedApiKeyResponse response = new CreatedApiKeyResponse(
                UUID.randomUUID(), "Production", "os_live_abcdefgh", PLAINTEXT_CREATED_KEY, createdAt);
        when(apiKeyService.createApiKey(principal, "Production")).thenReturn(response);

        MvcResult result = mockMvc.perform(authenticated(post("/api/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Production\",\"tenantId\":\"" + UUID.randomUUID() + "\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Production"))
                .andExpect(jsonPath("$.apiKey").value(PLAINTEXT_CREATED_KEY))
                .andReturn();

        assertEquals(1, occurrences(result.getResponse().getContentAsString(), PLAINTEXT_CREATED_KEY));
        verify(apiKeyService).createApiKey(principal, "Production");
    }

    @Test
    void listingReturnsOnlyResponseDtoFieldsForAuthenticatedTenant() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);
        UUID id = UUID.randomUUID();
        Instant timestamp = Instant.parse("2025-01-01T00:00:00Z");
        when(apiKeyService.getApiKeys(principal)).thenReturn(List.of(
                new ApiKeyResponse(id, "Production", "os_live_abcdefgh", ApiKeyStatus.ACTIVE, timestamp, null)));

        mockMvc.perform(authenticated(get("/api/v1/api-keys")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].name").value("Production"))
                .andExpect(jsonPath("$[0].keyPrefix").value("os_live_abcdefgh"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].keyHash").doesNotExist())
                .andExpect(jsonPath("$[0].apiKey").doesNotExist());

        verify(apiKeyService).getApiKeys(principal);
    }

    @Test
    void revocationReturnsUpdatedResponse() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);
        UUID id = UUID.randomUUID();
        Instant timestamp = Instant.parse("2025-01-01T00:00:00Z");
        ApiKeyResponse response = new ApiKeyResponse(
                id, "Production", "os_live_abcdefgh", ApiKeyStatus.REVOKED, timestamp, timestamp.plusSeconds(10));
        when(apiKeyService.revokeApiKey(principal, id)).thenReturn(response);

        mockMvc.perform(authenticated(delete("/api/v1/api-keys/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.keyHash").doesNotExist());

        verify(apiKeyService).revokeApiKey(principal, id);
    }

    @Test
    void anotherTenantsKeyReturns404() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);
        UUID id = UUID.randomUUID();
        when(apiKeyService.revokeApiKey(principal, id))
                .thenThrow(new NoSuchElementException("API key not found."));

        mockMvc.perform(authenticated(delete("/api/v1/api-keys/{id}", id)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/v1/api-keys/" + id));
    }

    @Test
    void repeatedRevocationReturns409() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);
        UUID id = UUID.randomUUID();
        when(apiKeyService.revokeApiKey(principal, id))
                .thenThrow(new IllegalStateException("API key has already been revoked."));

        mockMvc.perform(authenticated(delete("/api/v1/api-keys/{id}", id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void missingAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/api-keys"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(apiKeyService);
    }

    @Test
    void blankNameReturns400() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);

        mockMvc.perform(authenticated(post("/api/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(apiKeyService);
    }

    @Test
    void overlongNameReturns400() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);
        String tooLongName = "x".repeat(101);

        mockMvc.perform(authenticated(post("/api/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + tooLongName + "\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(apiKeyService);
    }

    private TenantPrincipal principal() {
        return new TenantPrincipal(UUID.randomUUID(), UUID.randomUUID());
    }

    private void authenticateAs(TenantPrincipal principal) {
        when(authenticationService.authenticate(API_KEY)).thenReturn(principal);
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.header("X-API-Key", API_KEY);
    }

    private int occurrences(String text, String value) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }
}
