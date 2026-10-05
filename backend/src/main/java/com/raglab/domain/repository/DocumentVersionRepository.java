package com.raglab.domain.repository;

import com.raglab.domain.entity.DocumentVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersionEntity, UUID> {
    List<DocumentVersionEntity> findAllByDocumentIdOrderByVersionNumberAsc(UUID documentId);
    Optional<DocumentVersionEntity> findByDocumentIdAndVersionNumber(UUID documentId, Integer versionNumber);
}
