package com.raglab.domain.repository;

import com.raglab.domain.entity.ExperimentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExperimentRepository extends JpaRepository<ExperimentEntity, UUID> {
    List<ExperimentEntity> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<ExperimentEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
