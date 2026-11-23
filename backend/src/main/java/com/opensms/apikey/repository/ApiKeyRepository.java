package com.opensms.apikey.repository;

import com.opensms.apikey.entity.ApiKey;
import com.opensms.apikey.entity.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findAllByTenant_Id(UUID tenantId);

    List<ApiKey> findAllByTenant_IdAndStatus(UUID tenantId, ApiKeyStatus status);

    Optional<ApiKey> findByIdAndTenant_Id(UUID id, UUID tenantId);

    Optional<ApiKey> findByKeyHashAndStatus(String keyHash, ApiKeyStatus status);

    boolean existsByKeyHash(String keyHash);
}

