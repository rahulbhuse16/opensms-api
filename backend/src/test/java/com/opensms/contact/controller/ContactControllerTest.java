package com.opensms.contact.controller;

import com.opensms.common.exception.GlobalExceptionHandler;
import com.opensms.contact.dto.ContactPageResponse;
import com.opensms.contact.dto.ContactResponse;
import com.opensms.contact.dto.CreateContactRequest;
import com.opensms.contact.dto.UpdateContactRequest;
import com.opensms.contact.exception.DuplicateContactPhoneException;
import com.opensms.contact.service.ContactService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContactController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ContactControllerTest {

    private static final String API_KEY = "contact-test-api-key";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ContactService contactService;
    @MockitoBean private ApiKeyAuthenticationService authenticationService;

    @Test
    void createReturns201AndContactResponse() throws Exception {
        TenantPrincipal principal = principal();
        authenticate(principal);
        ContactResponse response = contactResponse();
        when(contactService.create(eq(principal), any(CreateContactRequest.class))).thenReturn(response);

        mockMvc.perform(authenticated(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice\",\"phoneNumber\":\"+14155552671\","
                                + "\"email\":\"alice@example.test\",\"description\":\"VIP\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.phoneNumber").value("+14155552671"))
                .andExpect(jsonPath("$.email").value("alice@example.test"))
                .andExpect(jsonPath("$.description").value("VIP"));

        verify(contactService).create(eq(principal), any(CreateContactRequest.class));
    }

    @Test
    void requiredFieldsE164AndEmailValidationReturn400() throws Exception {
        TenantPrincipal principal = principal();
        authenticate(principal);
        List<String> invalidBodies = List.of(
                "{\"name\":\"  \",\"phoneNumber\":\"+14155552671\"}",
                "{\"name\":\"Alice\",\"phoneNumber\":\"  \"}",
                "{\"name\":\"Alice\",\"phoneNumber\":\"4155552671\"}",
                "{\"name\":\"Alice\",\"phoneNumber\":\"+14155552671\",\"email\":\"not-an-email\"}",
                "{\"name\":\"" + "x".repeat(151) + "\",\"phoneNumber\":\"+14155552671\"}",
                "{\"name\":\"Alice\",\"phoneNumber\":\"+14155552671\",\"description\":\""
                        + "x".repeat(1001) + "\"}");

        for (String body : invalidBodies) {
            mockMvc.perform(authenticated(post("/api/v1/contacts")
                            .contentType(MediaType.APPLICATION_JSON).content(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
        verifyNoInteractions(contactService);
    }

    @Test
    void getAndCrossTenantNotFoundUseTenantScopedService() throws Exception {
        TenantPrincipal principal = principal();
        authenticate(principal);
        ContactResponse response = contactResponse();
        when(contactService.get(principal, response.id())).thenReturn(response);
        UUID otherId = UUID.randomUUID();
        when(contactService.get(principal, otherId)).thenThrow(new NoSuchElementException("Contact not found"));

        mockMvc.perform(authenticated(get("/api/v1/contacts/{id}", response.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("+14155552671"));
        mockMvc.perform(authenticated(get("/api/v1/contacts/{id}", otherId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verify(contactService).get(principal, response.id());
        verify(contactService).get(principal, otherId);
    }

    @Test
    void listingUsesStructuredPaginationAndSearchParameter() throws Exception {
        TenantPrincipal principal = principal();
        authenticate(principal);
        ContactResponse response = contactResponse();
        when(contactService.list(principal, 0, 20, "Alice"))
                .thenReturn(new ContactPageResponse(List.of(response), 0, 20, 1, 1, false, false));

        mockMvc.perform(authenticated(get("/api/v1/contacts").queryParam("search", "Alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(response.id().toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(contactService).list(principal, 0, 20, "Alice");
    }

    @Test
    void updateDeleteDuplicateAndPaginationErrorsUseExpectedStatuses() throws Exception {
        TenantPrincipal principal = principal();
        authenticate(principal);
        ContactResponse response = contactResponse();
        when(contactService.update(eq(principal), eq(response.id()), any(UpdateContactRequest.class)))
                .thenReturn(response);
        UUID duplicateId = UUID.randomUUID();
        when(contactService.update(eq(principal), eq(duplicateId), any(UpdateContactRequest.class)))
                .thenThrow(new DuplicateContactPhoneException());
        when(contactService.list(principal, -1, 20, null))
                .thenThrow(new IllegalArgumentException("Page number must not be negative."));
        when(contactService.list(principal, 0, 101, null))
                .thenThrow(new IllegalArgumentException("Invalid page size."));

        String updateBody = "{\"name\":\"Alice\",\"phoneNumber\":\"+14155552671\"}";
        mockMvc.perform(authenticated(put("/api/v1/contacts/{id}", response.id())
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody)))
                .andExpect(status().isOk());
        mockMvc.perform(authenticated(delete("/api/v1/contacts/{id}", response.id())))
                .andExpect(status().isNoContent());
        mockMvc.perform(authenticated(put("/api/v1/contacts/{id}", duplicateId)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        mockMvc.perform(authenticated(get("/api/v1/contacts").queryParam("page", "-1")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(authenticated(get("/api/v1/contacts").queryParam("size", "101")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedContactRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/contacts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(contactService);
    }

    private ContactResponse contactResponse() {
        Instant timestamp = Instant.parse("2025-01-01T00:00:00Z");
        return new ContactResponse(UUID.randomUUID(), "Alice", "+14155552671", "alice@example.test",
                "VIP", timestamp, timestamp);
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
