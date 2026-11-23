package com.opensms.contact.controller;

import com.opensms.contact.dto.ContactPageResponse;
import com.opensms.contact.dto.ContactResponse;
import com.opensms.contact.dto.CreateContactRequest;
import com.opensms.contact.dto.UpdateContactRequest;
import com.opensms.contact.service.ContactService;
import com.opensms.security.TenantPrincipal;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contacts")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping
    public ResponseEntity<ContactResponse> create(
            @Valid @RequestBody CreateContactRequest request,
            @AuthenticationPrincipal TenantPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contactService.create(principal, request));
    }

    @GetMapping
    public ResponseEntity<ContactPageResponse> list(
            @AuthenticationPrincipal TenantPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(contactService.list(principal, page, size, search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContactResponse> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal TenantPrincipal principal
    ) {
        return ResponseEntity.ok(contactService.get(principal, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContactResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateContactRequest request,
            @AuthenticationPrincipal TenantPrincipal principal
    ) {
        return ResponseEntity.ok(contactService.update(principal, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal TenantPrincipal principal
    ) {
        contactService.delete(principal, id);
        return ResponseEntity.noContent().build();
    }
}
