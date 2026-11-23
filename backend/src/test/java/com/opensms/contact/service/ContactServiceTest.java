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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock private ContactRepository contactRepository;
    @Mock private TenantRepository tenantRepository;

    private ContactService service;

    @BeforeEach
    void setUp() {
        service = new ContactService(contactRepository, tenantRepository, new ContactMapper());
    }

    @Test
    void createsContactForAuthenticatedTenantAndTrimsOptionalText() {
        Tenant tenant = new Tenant("Acme");
        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(contactRepository.existsByTenant_IdAndPhoneNumber(tenant.getId(), "+14155552671"))
                .thenReturn(false);
        when(contactRepository.saveAndFlush(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContactResponse response = service.create(principal(tenant),
                new CreateContactRequest("  Alice Smith  ", "+14155552671", " alice@example.test ", "  VIP  "));

        assertEquals("Alice Smith", response.name());
        assertEquals("+14155552671", response.phoneNumber());
        assertEquals("alice@example.test", response.email());
        assertEquals("VIP", response.description());
        ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).saveAndFlush(captor.capture());
        assertEquals(tenant.getId(), captor.getValue().getTenant().getId());
    }

    @Test
    void retrievesContactByTenantScopedId() {
        Tenant tenant = new Tenant("Acme");
        Contact contact = contact(tenant, "Alice", "+14155552671");
        when(contactRepository.findByIdAndTenant_Id(contact.getId(), tenant.getId())).thenReturn(Optional.of(contact));

        ContactResponse response = service.get(principal(tenant), contact.getId());

        assertEquals(contact.getId(), response.id());
        assertEquals("Alice", response.name());
        verify(contactRepository).findByIdAndTenant_Id(contact.getId(), tenant.getId());
    }

    @Test
    void listsTenantPageAndSearchesByNameOrPhonePrefix() {
        Tenant tenant = new Tenant("Acme");
        Contact contact = contact(tenant, "Alice", "+14155552671");
        when(contactRepository.findPageByTenantId(eq(tenant.getId()), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(contact), PageRequest.of(0, 20), 1));
        when(contactRepository.searchPageByTenantId(eq(tenant.getId()), eq("ali%"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(contact), PageRequest.of(0, 20), 1));
        when(contactRepository.searchPageByTenantId(eq(tenant.getId()), eq("+1415%"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(contact), PageRequest.of(0, 20), 1));

        ContactPageResponse defaultPage = service.list(principal(tenant), 0, 20, null);
        ContactPageResponse byName = service.list(principal(tenant), 0, 20, " ALI ");
        ContactPageResponse byPhone = service.list(principal(tenant), 0, 20, "+1415");

        assertEquals(1, defaultPage.totalElements());
        assertEquals(1, byName.content().size());
        assertEquals(1, byPhone.content().size());
        verify(contactRepository).findPageByTenantId(tenant.getId(), PageRequest.of(0, 20));
        verify(contactRepository).searchPageByTenantId(tenant.getId(), "ali%", PageRequest.of(0, 20));
        verify(contactRepository).searchPageByTenantId(tenant.getId(), "+1415%", PageRequest.of(0, 20));
        verify(contactRepository, never()).findAll();
    }

    @Test
    void escapesLikeWildcardsInSearchText() {
        Tenant tenant = new Tenant("Acme");
        when(contactRepository.searchPageByTenantId(eq(tenant.getId()), eq("a!%b!_!!%"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        service.list(principal(tenant), 0, 10, "A%B_!");

        verify(contactRepository).searchPageByTenantId(tenant.getId(), "a!%b!_!!%", PageRequest.of(0, 10));
    }

    @Test
    void updatesAndDeletesOnlyTenantOwnedContact() {
        Tenant tenant = new Tenant("Acme");
        Contact contact = contact(tenant, "Alice", "+14155552671");
        when(contactRepository.findByIdAndTenant_Id(contact.getId(), tenant.getId())).thenReturn(Optional.of(contact));
        when(contactRepository.saveAndFlush(contact)).thenReturn(contact);

        ContactResponse updated = service.update(principal(tenant), contact.getId(),
                new UpdateContactRequest("Alice Jones", "+14155552672", null, "Updated"));
        service.delete(principal(tenant), contact.getId());

        assertEquals("Alice Jones", updated.name());
        assertEquals("+14155552672", updated.phoneNumber());
        assertEquals("Updated", updated.description());
        verify(contactRepository).existsByTenant_IdAndPhoneNumberAndIdNot(
                tenant.getId(), "+14155552672", contact.getId());
        verify(contactRepository).saveAndFlush(contact);
        verify(contactRepository).delete(contact);
    }

    @Test
    void duplicatePhoneIsRejectedWithinTenantButAllowedForAnotherTenant() {
        Tenant tenantOne = new Tenant("One");
        Tenant tenantTwo = new Tenant("Two");
        when(tenantRepository.findById(tenantOne.getId())).thenReturn(Optional.of(tenantOne));
        when(tenantRepository.findById(tenantTwo.getId())).thenReturn(Optional.of(tenantTwo));
        when(contactRepository.existsByTenant_IdAndPhoneNumber(tenantOne.getId(), "+14155552671"))
                .thenReturn(true);
        when(contactRepository.existsByTenant_IdAndPhoneNumber(tenantTwo.getId(), "+14155552671"))
                .thenReturn(false);
        when(contactRepository.saveAndFlush(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(DuplicateContactPhoneException.class, () -> service.create(principal(tenantOne),
                new CreateContactRequest("Alice", "+14155552671", null, null)));
        ContactResponse otherTenantContact = service.create(principal(tenantTwo),
                new CreateContactRequest("Alice", "+14155552671", null, null));

        assertEquals("+14155552671", otherTenantContact.phoneNumber());
        verify(contactRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.argThat(
                contact -> contact.getTenant().getId().equals(tenantOne.getId())));
    }

    @Test
    void crossTenantGetUpdateAndDeleteBehaveAsNotFound() {
        Tenant tenant = new Tenant("Acme");
        UUID id = UUID.randomUUID();
        when(contactRepository.findByIdAndTenant_Id(id, tenant.getId())).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.get(principal(tenant), id));
        assertThrows(NoSuchElementException.class, () -> service.update(principal(tenant), id,
                new UpdateContactRequest("Alice", "+14155552671", null, null)));
        assertThrows(NoSuchElementException.class, () -> service.delete(principal(tenant), id));
        verifyNoInteractions(tenantRepository);
    }

    @Test
    void rejectsInvalidPaginationAndOversizedSearch() {
        TenantPrincipal principal = principal(new Tenant("Acme"));

        assertThrows(IllegalArgumentException.class, () -> service.list(principal, -1, 20, null));
        assertThrows(IllegalArgumentException.class, () -> service.list(principal, 0, 0, null));
        assertThrows(IllegalArgumentException.class, () -> service.list(principal, 0, 101, null));
        assertThrows(IllegalArgumentException.class, () -> service.list(principal, 0, 20, "x".repeat(151)));
        verifyNoInteractions(contactRepository);
    }

    private Contact contact(Tenant tenant, String name, String phone) {
        return new Contact(tenant, name, phone, null, null);
    }

    private TenantPrincipal principal(Tenant tenant) {
        return new TenantPrincipal(tenant.getId(), UUID.randomUUID());
    }
}
