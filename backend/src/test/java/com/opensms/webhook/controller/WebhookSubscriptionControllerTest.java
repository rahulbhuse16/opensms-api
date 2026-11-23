package com.opensms.webhook.controller;

import com.opensms.common.exception.GlobalExceptionHandler;
import com.opensms.security.ApiKeyAuthenticationService;
import com.opensms.security.SecurityConfig;
import com.opensms.security.TenantPrincipal;
import com.opensms.webhook.dto.CreatedWebhookSubscriptionResponse;
import com.opensms.webhook.dto.WebhookSubscriptionResponse;
import com.opensms.webhook.entity.WebhookEventType;
import com.opensms.webhook.entity.WebhookSubscriptionStatus;
import com.opensms.webhook.service.WebhookSubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookSubscriptionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class WebhookSubscriptionControllerTest {

    private static final String API_KEY = "webhook-test-key";
    private static final String URL = "https://hooks.example.test/events";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private WebhookSubscriptionService subscriptionService;
    @MockitoBean private ApiKeyAuthenticationService authenticationService;

    @Test
    void authenticatedCreateReturnsSecretOnce() throws Exception {
        TenantPrincipal principal = principal();
        authenticate(principal);
        UUID id = UUID.randomUUID();
        when(subscriptionService.create(org.mockito.ArgumentMatchers.eq(principal), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new CreatedWebhookSubscriptionResponse(id, URL,
                        List.of(WebhookEventType.MESSAGE_QUEUED), WebhookSubscriptionStatus.ACTIVE,
                        Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-01-01T00:00:00Z"),
                        "whsec_once"));

        mockMvc.perform(authenticated(post("/api/v1/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + URL + "\",\"eventTypes\":[\"message.queued\"]}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.eventTypes[0]").value("message.queued"))
                .andExpect(jsonPath("$.signingSecret").value("whsec_once"));

        verify(subscriptionService).create(org.mockito.ArgumentMatchers.eq(principal), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listAndGetResponsesDoNotExposeSigningSecrets() throws Exception {
        TenantPrincipal principal = principal();
        authenticate(principal);
        WebhookSubscriptionResponse response = normalResponse();
        when(subscriptionService.list(principal)).thenReturn(List.of(response));
        when(subscriptionService.get(principal, response.id())).thenReturn(response);

        mockMvc.perform(authenticated(get("/api/v1/webhooks")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(response.id().toString()))
                .andExpect(jsonPath("$[0].signingSecret").doesNotExist())
                .andExpect(jsonPath("$[0].encryptedSigningSecret").doesNotExist());
        mockMvc.perform(authenticated(get("/api/v1/webhooks/{id}", response.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(URL))
                .andExpect(jsonPath("$.signingSecret").doesNotExist());

        verify(subscriptionService).list(principal);
        verify(subscriptionService).get(principal, response.id());
    }

    @Test
    void updateAndDeleteReturnTenantSubscriptionResult() throws Exception {
        TenantPrincipal principal = principal();
        authenticate(principal);
        WebhookSubscriptionResponse response = normalResponse();
        when(subscriptionService.update(org.mockito.ArgumentMatchers.eq(principal),
                org.mockito.ArgumentMatchers.eq(response.id()), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response);

        mockMvc.perform(authenticated(put("/api/v1/webhooks/{id}", response.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + URL + "\",\"eventTypes\":[\"message.sent\"],\"status\":\"SUSPENDED\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signingSecret").doesNotExist());
        mockMvc.perform(authenticated(delete("/api/v1/webhooks/{id}", response.id())))
                .andExpect(status().isNoContent());

        verify(subscriptionService).update(org.mockito.ArgumentMatchers.eq(principal),
                org.mockito.ArgumentMatchers.eq(response.id()), org.mockito.ArgumentMatchers.any());
        verify(subscriptionService).delete(principal, response.id());
    }

    @Test
    void crossTenantSubscriptionIsReportedAsNotFound() throws Exception {
        TenantPrincipal principal = principal();
        authenticate(principal);
        UUID id = UUID.randomUUID();
        when(subscriptionService.get(principal, id)).thenThrow(new NoSuchElementException("Not found"));

        mockMvc.perform(authenticated(get("/api/v1/webhooks/{id}", id)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void invalidUrlsAndEmptyOrUnsupportedEventsReturn400() throws Exception {
        TenantPrincipal principal = principal();
        authenticate(principal);
        for (String invalidUrl : List.of(
                "http://hooks.example.test/events",
                "https://user:password@hooks.example.test/events",
                "https://hooks.example.test/events#fragment",
                "https://bad host/events")) {
            mockMvc.perform(authenticated(post("/api/v1/webhooks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"" + invalidUrl + "\",\"eventTypes\":[\"message.queued\"]}")))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(authenticated(post("/api/v1/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + URL + "\",\"eventTypes\":[]}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(authenticated(post("/api/v1/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + URL + "\",\"eventTypes\":[\"message.delivered\"]}")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(subscriptionService);
    }

    @Test
    void allWebhookRoutesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(subscriptionService);
    }

    private WebhookSubscriptionResponse normalResponse() {
        return new WebhookSubscriptionResponse(UUID.randomUUID(), URL,
                List.of(WebhookEventType.MESSAGE_QUEUED), WebhookSubscriptionStatus.ACTIVE,
                Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-01-01T00:00:00Z"));
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
