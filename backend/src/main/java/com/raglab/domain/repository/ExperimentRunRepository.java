package com.raglab.domain.repository;

import com.raglab.domain.entity.ExperimentRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExperimentRunRepository extends JpaRepository<ExperimentRunEntity, UUID> {
    List<ExperimentRunEntity> findAllByExperimentIdOrderByCreatedAtDesc(UUID experimentId);
    List<ExperimentRunEntity> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<ExperimentRunEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
