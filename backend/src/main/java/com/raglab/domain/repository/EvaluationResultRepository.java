package com.raglab.domain.repository;

import com.raglab.domain.entity.EvaluationResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EvaluationResultRepository extends JpaRepository<EvaluationResultEntity, UUID> {
    List<EvaluationResultEntity> findAllByRunIdOrderByCreatedAtAsc(UUID runId);
}
