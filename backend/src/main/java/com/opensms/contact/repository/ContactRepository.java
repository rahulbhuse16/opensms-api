package com.opensms.contact.repository;

import com.opensms.contact.entity.Contact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    boolean existsByTenant_IdAndPhoneNumber(UUID tenantId, String phoneNumber);

    boolean existsByTenant_IdAndPhoneNumberAndIdNot(UUID tenantId, String phoneNumber, UUID id);

    Optional<Contact> findByIdAndTenant_Id(UUID id, UUID tenantId);

    @Query(value = "SELECT * FROM contacts WHERE tenant_id = :tenantId "
            + "ORDER BY created_at DESC, id DESC",
            countQuery = "SELECT COUNT(*) FROM contacts WHERE tenant_id = :tenantId",
            nativeQuery = true)
    Page<Contact> findPageByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query(value = "SELECT * FROM contacts WHERE tenant_id = :tenantId "
            + "AND (LOWER(name) LIKE :pattern ESCAPE '!' OR phone_number LIKE :pattern ESCAPE '!') "
            + "ORDER BY created_at DESC, id DESC",
            countQuery = "SELECT COUNT(*) FROM contacts WHERE tenant_id = :tenantId "
                    + "AND (LOWER(name) LIKE :pattern ESCAPE '!' OR phone_number LIKE :pattern ESCAPE '!')",
            nativeQuery = true)
    Page<Contact> searchPageByTenantId(
            @Param("tenantId") UUID tenantId,
            @Param("pattern") String pattern,
            Pageable pageable
    );
}
