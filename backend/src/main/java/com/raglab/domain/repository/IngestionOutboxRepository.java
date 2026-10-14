package com.raglab.domain.repository;

import com.raglab.domain.entity.IngestionOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IngestionOutboxRepository extends JpaRepository<IngestionOutboxEntity, UUID> {
    List<IngestionOutboxEntity> findTop50ByPublishedFalseOrderByCreatedAtAsc();
}

