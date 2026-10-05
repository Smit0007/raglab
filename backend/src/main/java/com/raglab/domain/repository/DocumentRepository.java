package com.raglab.domain.repository;

import com.raglab.domain.entity.DocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {
    List<DocumentEntity> findAllByTenantId(UUID tenantId);
    Page<DocumentEntity> findAllByTenantId(UUID tenantId, Pageable pageable);
    Page<DocumentEntity> findAllByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
    Optional<DocumentEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<DocumentEntity> findByTenantIdAndFileHashSha256(UUID tenantId, String fileHashSha256);
}
