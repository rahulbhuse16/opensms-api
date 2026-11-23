package com.opensms.message.controller;

import com.opensms.message.dto.CreateMessageRequest;
import com.opensms.message.dto.MessagePageResponse;
import com.opensms.message.dto.MessageResponse;
import com.opensms.message.dto.MessageStatusHistoryResponse;
import com.opensms.message.entity.MessageStatus;
import com.opensms.message.idempotency.IdempotencyKeyConflictException;
import com.opensms.message.service.MessageService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MessageController.class)
@Import(SecurityConfig.class)
class MessageControllerTest {

    private static final String API_KEY = "message-test-api-key";
    private static final String DESTINATION = "+14155552671";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessageService messageService;

    @MockitoBean
    private ApiKeyAuthenticationService authenticationService;

    @Test
    void successfulSubmissionReturns202AndQueuedMessage() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);
        MessageResponse response = messageResponse(DESTINATION, "Hello", MessageStatus.QUEUED);
        when(messageService.createMessage(new CreateMessageRequest(DESTINATION, "Hello"), principal, null))
                .thenReturn(response);

        mockMvc.perform(authenticated(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationPhoneNumber\":\"" + DESTINATION + "\",\"body\":\"Hello\"}")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.destinationPhoneNumber").value(DESTINATION))
                .andExpect(jsonPath("$.body").value("Hello"))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        verify(messageService).createMessage(new CreateMessageRequest(DESTINATION, "Hello"), principal, null);
    }

    @Test
    void forwardsOptionalIdempotencyHeaderWithoutChangingRequestBody() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);
        MessageResponse response = messageResponse(DESTINATION, "Hello", MessageStatus.QUEUED);
        when(messageService.createMessage(new CreateMessageRequest(DESTINATION, "Hello"), principal, "key-123"))
                .thenReturn(response);

        mockMvc.perform(authenticated(post("/api/v1/messages")
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationPhoneNumber\":\"" + DESTINATION + "\",\"body\":\"Hello\"}")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(response.id().toString()));

        verify(messageService).createMessage(new CreateMessageRequest(DESTINATION, "Hello"), principal, "key-123");
    }

    @Test
    void malformedIdempotencyKeyReturns400() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);
        when(messageService.createMessage(new CreateMessageRequest(DESTINATION, "Hello"), principal, "bad key"))
                .thenThrow(new IllegalArgumentException("Idempotency-Key must contain 1 to 128 visible ASCII characters with no whitespace."));

        mockMvc.perform(authenticated(post("/api/v1/messages")
                        .header("Idempotency-Key", "bad key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationPhoneNumber\":\"" + DESTINATION + "\",\"body\":\"Hello\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void payloadMismatchForExistingKeyReturns409() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);
        when(messageService.createMessage(new CreateMessageRequest(DESTINATION, "Hello"), principal, "key-123"))
                .thenThrow(new IdempotencyKeyConflictException());

        mockMvc.perform(authenticated(post("/api/v1/messages")
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationPhoneNumber\":\"" + DESTINATION + "\",\"body\":\"Hello\"}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void listUsesDefaultPageAndSize() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);
        MessageResponse response = messageResponse(DESTINATION, "Hello", MessageStatus.QUEUED);
        MessagePageResponse page = new MessagePageResponse(List.of(response), 0, 20, 1, 1, false, false);
        when(messageService.getMessages(principal, 0, 20, null)).thenReturn(page);

        mockMvc.perform(authenticated(get("/api/v1/messages")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(response.id().toString()))
                .andExpect(jsonPath("$.content[0].destinationPhoneNumber").value(DESTINATION))
                .andExpect(jsonPath("$.content[0].body").value("Hello"))
                .andExpect(jsonPath("$.content[0].status").value("QUEUED"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        verify(messageService).getMessages(principal, 0, 20, null);
    }

    @Test
    void listPassesCustomPageSizeAndStatusFilter() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);
        when(messageService.getMessages(principal, 2, 10, MessageStatus.FAILED))
                .thenReturn(new MessagePageResponse(List.of(), 2, 10, 0, 0, false, true));

        mockMvc.perform(authenticated(get("/api/v1/messages")
                        .queryParam("page", "2")
                        .queryParam("size", "10")
                        .queryParam("status", "FAILED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.hasPrevious").value(true));

        verify(messageService).getMessages(principal, 2, 10, MessageStatus.FAILED);
    }

    @Test
    void invalidPageAndSizeReturn400() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);

        mockMvc.perform(authenticated(get("/api/v1/messages").queryParam("page", "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(authenticated(get("/api/v1/messages").queryParam("size", "101")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(authenticated(get("/api/v1/messages").queryParam("size", "abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void historyEndpointReturnsOnlyMessageHistoryDtoFields() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);
        UUID messageId = UUID.randomUUID();
        MessageStatusHistoryResponse history = new MessageStatusHistoryResponse(
                UUID.randomUUID(), messageId, null, MessageStatus.QUEUED, "API", Instant.parse("2025-01-01T00:00:00Z"));
        when(messageService.getMessageHistory(messageId, principal)).thenReturn(List.of(history));

        mockMvc.perform(authenticated(get("/api/v1/messages/{id}/history", messageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(history.id().toString()))
                .andExpect(jsonPath("$[0].messageId").value(messageId.toString()))
                .andExpect(jsonPath("$[0].previousStatus").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].newStatus").value("QUEUED"))
                .andExpect(jsonPath("$[0].changeSource").value("API"))
                .andExpect(jsonPath("$[0].timestamp").value(history.timestamp().toString()));

        verify(messageService).getMessageHistory(messageId, principal);
    }

    @Test
    void historyEndpointReturns404ForMissingOrCrossTenantMessage() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);
        UUID messageId = UUID.randomUUID();
        when(messageService.getMessageHistory(messageId, principal))
                .thenThrow(new NoSuchElementException("Message not found: " + messageId));

        mockMvc.perform(authenticated(get("/api/v1/messages/{id}/history", messageId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void historyEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/messages/{id}/history", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(messageService);
    }

    @Test
    void detailReturnsTenantOwnedMessage() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);
        MessageResponse response = messageResponse(DESTINATION, "Hello", MessageStatus.SENT);
        when(messageService.getMessageById(response.id(), principal)).thenReturn(response);

        mockMvc.perform(authenticated(get("/api/v1/messages/{id}", response.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.status").value("SENT"));

        verify(messageService).getMessageById(response.id(), principal);
    }

    @Test
    void invalidPhoneNumberReturns400() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);

        mockMvc.perform(authenticated(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationPhoneNumber\":\"4155552671\",\"body\":\"Hello\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(messageService);
    }

    @Test
    void blankBodyReturns400() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);

        mockMvc.perform(authenticated(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationPhoneNumber\":\"" + DESTINATION + "\",\"body\":\"  \"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(messageService);
    }

    @Test
    void unauthenticatedMessageRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/messages"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(messageService);
    }

    @Test
    void missingMessageReturns404() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);
        UUID messageId = UUID.randomUUID();
        when(messageService.getMessageById(messageId, principal))
                .thenThrow(new NoSuchElementException("Message not found: " + messageId));

        mockMvc.perform(authenticated(get("/api/v1/messages/{id}", messageId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/v1/messages/" + messageId));
    }

    @Test
    void crossTenantMessageReturns404() throws Exception {
        TenantPrincipal principal = principal();
        authenticateAs(principal);
        UUID messageId = UUID.randomUUID();
        when(messageService.getMessageById(messageId, principal))
                .thenThrow(new NoSuchElementException("Message not found: " + messageId));

        mockMvc.perform(authenticated(get("/api/v1/messages/{id}", messageId)))
                .andExpect(status().isNotFound());
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

    private MessageResponse messageResponse(String destination, String body, MessageStatus status) {
        Instant timestamp = Instant.parse("2025-01-01T00:00:00Z");
        return new MessageResponse(UUID.randomUUID(), destination, body, status, timestamp, timestamp);
    }
}
