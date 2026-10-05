package com.raglab.domain.repository;

import com.raglab.domain.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    List<UserEntity> findAllByTenantId(UUID tenantId);
    Optional<UserEntity> findByTenantIdAndEmail(UUID tenantId, String email);
}
