package com.raglab.domain.repository;

import com.raglab.domain.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {
    List<ConversationEntity> findAllByTenantIdOrderByUpdatedAtDesc(UUID tenantId);
    List<ConversationEntity> findAllByTenantIdAndUserIdOrderByUpdatedAtDesc(UUID tenantId, UUID userId);
    Optional<ConversationEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
