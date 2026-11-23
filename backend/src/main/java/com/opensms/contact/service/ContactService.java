package com.opensms.contact.service;

import com.opensms.contact.dto.ContactPageResponse;
import com.opensms.contact.dto.ContactResponse;
import com.opensms.contact.dto.CreateContactRequest;
import com.opensms.contact.dto.UpdateContactRequest;
import com.opensms.contact.entity.Contact;
import com.opensms.contact.exception.DuplicateContactPhoneException;
import com.opensms.contact.mapper.ContactMapper;
import com.opensms.contact.repository.ContactRepository;
import com.opensms.security.TenantPrincipal;
import com.opensms.tenant.entity.Tenant;
import com.opensms.tenant.repository.TenantRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ContactService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SEARCH_LENGTH = 150;
    private static final String PHONE_UNIQUE_CONSTRAINT = "uq_contacts_tenant_phone";

    private final ContactRepository contactRepository;
    private final TenantRepository tenantRepository;
    private final ContactMapper mapper;

    public ContactService(ContactRepository contactRepository, TenantRepository tenantRepository, ContactMapper mapper) {
        this.contactRepository = contactRepository;
        this.tenantRepository = tenantRepository;
        this.mapper = mapper;
    }

    @Transactional
    public ContactResponse create(TenantPrincipal principal, CreateContactRequest request) {
        UUID tenantId = tenantId(principal);
        if (request == null) throw new IllegalArgumentException("Contact request must not be null.");
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("Tenant not found: " + tenantId));
        if (contactRepository.existsByTenant_IdAndPhoneNumber(tenantId, request.phoneNumber())) {
            throw new DuplicateContactPhoneException();
        }
        Contact contact = new Contact(tenant, request.name(), request.phoneNumber(), request.email(),
                request.description());
        return mapper.toResponse(saveOrTranslateDuplicate(contact));
    }

    @Transactional(readOnly = true)
    public ContactResponse get(TenantPrincipal principal, UUID id) {
        return mapper.toResponse(findOwned(tenantId(principal), id));
    }

    @Transactional(readOnly = true)
    public ContactPageResponse list(TenantPrincipal principal, int page, int size, String search) {
        if (page < 0) throw new IllegalArgumentException("Page number must not be negative.");
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        UUID tenantId = tenantId(principal);
        String normalizedSearch = normalizeSearch(search);
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Contact> contacts = normalizedSearch == null
                ? contactRepository.findPageByTenantId(tenantId, pageRequest)
                : contactRepository.searchPageByTenantId(tenantId, toLikePrefix(normalizedSearch), pageRequest);
        Page<ContactResponse> responses = contacts.map(mapper::toResponse);
        return new ContactPageResponse(responses.getContent(), responses.getNumber(), responses.getSize(),
                responses.getTotalElements(), responses.getTotalPages(), responses.hasNext(),
                responses.hasPrevious());
    }

    @Transactional
    public ContactResponse update(TenantPrincipal principal, UUID id, UpdateContactRequest request) {
        UUID tenantId = tenantId(principal);
        if (request == null) throw new IllegalArgumentException("Contact request must not be null.");
        Contact contact = findOwned(tenantId, id);
        if (!contact.getPhoneNumber().equals(request.phoneNumber())
                && contactRepository.existsByTenant_IdAndPhoneNumberAndIdNot(
                        tenantId, request.phoneNumber(), contact.getId())) {
            throw new DuplicateContactPhoneException();
        }
        contact.updateDetails(request.name(), request.phoneNumber(), request.email(), request.description());
        return mapper.toResponse(saveOrTranslateDuplicate(contact));
    }

    @Transactional
    public void delete(TenantPrincipal principal, UUID id) {
        contactRepository.delete(findOwned(tenantId(principal), id));
    }

    private Contact findOwned(UUID tenantId, UUID id) {
        if (id == null) throw new IllegalArgumentException("Contact ID must not be null.");
        return contactRepository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Contact not found: " + id));
    }

    private Contact saveOrTranslateDuplicate(Contact contact) {
        try {
            return contactRepository.saveAndFlush(contact);
        } catch (DataIntegrityViolationException exception) {
            if (isPhoneUniqueViolation(exception)) throw new DuplicateContactPhoneException();
            throw exception;
        }
    }

    private boolean isPhoneUniqueViolation(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && PHONE_UNIQUE_CONSTRAINT.equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) return null;
        String value = search.strip();
        if (value.length() > MAX_SEARCH_LENGTH) {
            throw new IllegalArgumentException("Search text must not exceed " + MAX_SEARCH_LENGTH + " characters.");
        }
        return value;
    }

    private String toLikePrefix(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_") + "%";
    }

    private UUID tenantId(TenantPrincipal principal) {
        if (principal == null) throw new IllegalArgumentException("Authenticated tenant principal is required.");
        return principal.tenantId();
    }
}
