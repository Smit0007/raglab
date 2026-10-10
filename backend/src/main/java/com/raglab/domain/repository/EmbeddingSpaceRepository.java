package com.raglab.domain.repository;

import com.raglab.domain.entity.EmbeddingSpaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmbeddingSpaceRepository extends JpaRepository<EmbeddingSpaceEntity, String> {
    Optional<EmbeddingSpaceEntity> findByIdAndActiveTrue(String id);
}

