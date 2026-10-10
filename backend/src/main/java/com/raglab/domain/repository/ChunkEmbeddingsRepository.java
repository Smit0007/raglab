package com.raglab.domain.repository;

import com.raglab.domain.entity.ChunkEmbeddingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChunkEmbeddingsRepository extends JpaRepository<ChunkEmbeddingsEntity, UUID> {
    List<ChunkEmbeddingsEntity> findAllByTenantIdAndSpaceId(UUID tenantId, String spaceId);
    Optional<ChunkEmbeddingsEntity> findByChunkIdAndSpaceId(UUID chunkId, String spaceId);
    void deleteAllByChunkId(UUID chunkId);
}

