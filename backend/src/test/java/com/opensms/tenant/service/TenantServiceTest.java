package com.opensms.tenant.service;

import com.opensms.tenant.dto.TenantResponse;
import com.opensms.tenant.entity.Tenant;
import com.opensms.tenant.entity.TenantStatus;
import com.opensms.tenant.mapper.TenantMapper;
import com.opensms.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantMapper tenantMapper;

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(tenantRepository, tenantMapper);
    }

    @Test
    void createTenantSavesActiveTenantAndReturnsResponse() {
        TenantResponse expected = response("Acme");
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tenantMapper.toResponse(any(Tenant.class))).thenReturn(expected);

        TenantResponse result = tenantService.createTenant("Acme");

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertEquals("Acme", tenantCaptor.getValue().getName());
        assertEquals(TenantStatus.ACTIVE, tenantCaptor.getValue().getStatus());
        verify(tenantMapper).toResponse(tenantCaptor.getValue());
        assertSame(expected, result);
    }

    @Test
    void createTenantTrimsNameBeforeSaving() {
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tenantMapper.toResponse(any(Tenant.class))).thenReturn(response("Acme"));

        tenantService.createTenant("  Acme  ");

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertEquals("Acme", tenantCaptor.getValue().getName());
    }

    @Test
    void createTenantRejectsNullAndBlankNames() {
        assertThrows(IllegalArgumentException.class, () -> tenantService.createTenant(null));
        assertThrows(IllegalArgumentException.class, () -> tenantService.createTenant(" \t "));

        verifyNoInteractions(tenantRepository, tenantMapper);
    }

    @Test
    void createTenantRejectsNamesLongerThan150Characters() {
        String tooLongName = "a".repeat(151);

        assertThrows(IllegalArgumentException.class, () -> tenantService.createTenant(tooLongName));

        verifyNoInteractions(tenantRepository, tenantMapper);
    }

    @Test
    void getTenantByIdReturnsMappedTenant() {
        UUID id = UUID.randomUUID();
        Tenant tenant = new Tenant("Acme");
        TenantResponse expected = response("Acme");
        when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));
        when(tenantMapper.toResponse(tenant)).thenReturn(expected);

        TenantResponse result = tenantService.getTenantById(id);

        assertSame(expected, result);
        verify(tenantRepository).findById(id);
        verify(tenantMapper).toResponse(tenant);
    }

    @Test
    void getTenantByIdThrowsWhenTenantIsMissing() {
        UUID id = UUID.randomUUID();
        when(tenantRepository.findById(id)).thenReturn(Optional.empty());

        NoSuchElementException exception = assertThrows(
                NoSuchElementException.class,
                () -> tenantService.getTenantById(id)
        );

        assertTrue(exception.getMessage().contains(id.toString()));
        verify(tenantRepository).findById(id);
        verifyNoInteractions(tenantMapper);
    }

    @Test
    void getAllTenantsReturnsMappedTenants() {
        Tenant first = new Tenant("Acme");
        Tenant second = new Tenant("Globex");
        TenantResponse firstResponse = response("Acme");
        TenantResponse secondResponse = response("Globex");
        when(tenantRepository.findAll()).thenReturn(List.of(first, second));
        when(tenantMapper.toResponse(first)).thenReturn(firstResponse);
        when(tenantMapper.toResponse(second)).thenReturn(secondResponse);

        List<TenantResponse> result = tenantService.getAllTenants();

        assertEquals(List.of(firstResponse, secondResponse), result);
        verify(tenantRepository).findAll();
        verify(tenantMapper).toResponse(first);
        verify(tenantMapper).toResponse(second);
    }

    @Test
    void tenantExistsReturnsRepositoryResult() {
        UUID id = UUID.randomUUID();
        when(tenantRepository.existsById(id)).thenReturn(true, false);

        assertTrue(tenantService.tenantExists(id));
        assertFalse(tenantService.tenantExists(id));

        verify(tenantRepository, org.mockito.Mockito.times(2)).existsById(id);
    }

    private TenantResponse response(String name) {
        Instant timestamp = Instant.parse("2025-01-01T00:00:00Z");
        return new TenantResponse(UUID.randomUUID(), name, TenantStatus.ACTIVE, timestamp, timestamp);
    }
}
