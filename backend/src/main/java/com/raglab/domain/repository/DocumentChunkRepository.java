package com.raglab.domain.repository;

import com.raglab.domain.entity.DocumentChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, UUID> {

    List<DocumentChunkEntity> findAllByDocumentIdOrderByChunkIndexAsc(UUID documentId);

    List<DocumentChunkEntity> findAllByTenantIdAndDocumentId(UUID tenantId, UUID documentId);

    void deleteAllByDocumentId(UUID documentId);

    /**
     * Cosine similarity search using pgvector `<=>` operator (cosine distance).
     * Cosine similarity = 1 - cosine_distance.
     */
    @Query(value = """
        SELECT c.*, (1 - (c.embedding <=> cast(:queryVector as vector))) AS similarity_score
        FROM document_chunks c
        WHERE c.tenant_id = :tenantId
          AND c.embedding IS NOT NULL
          AND (1 - (c.embedding <=> cast(:queryVector as vector))) >= :minScore
        ORDER BY c.embedding <=> cast(:queryVector as vector) ASC
        LIMIT :topK
        """, nativeQuery = true)
    List<DocumentChunkEntity> findSimilarChunks(
        @Param("tenantId") UUID tenantId,
        @Param("queryVector") String queryVector,
        @Param("topK") int topK,
        @Param("minScore") double minScore
    );

    /**
     * Cosine similarity search filtered by specific document IDs.
     */
    @Query(value = """
        SELECT c.*
        FROM document_chunks c
        WHERE c.tenant_id = :tenantId
          AND c.document_id IN (:documentIds)
          AND c.embedding IS NOT NULL
          AND (1 - (c.embedding <=> cast(:queryVector as vector))) >= :minScore
        ORDER BY c.embedding <=> cast(:queryVector as vector) ASC
        LIMIT :topK
        """, nativeQuery = true)
    List<DocumentChunkEntity> findSimilarChunksInDocuments(
        @Param("tenantId") UUID tenantId,
        @Param("documentIds") List<UUID> documentIds,
        @Param("queryVector") String queryVector,
        @Param("topK") int topK,
        @Param("minScore") double minScore
    );

    /**
     * Exact exhaustive k-NN search (ignoring HNSW index) for recall benchmarking.
     */
    @Query(value = """
        SET LOCAL enable_indexscan = off;
        SELECT c.*
        FROM document_chunks c
        WHERE c.tenant_id = :tenantId
          AND c.embedding IS NOT NULL
        ORDER BY c.embedding <=> cast(:queryVector as vector) ASC
        LIMIT :topK;
        """, nativeQuery = true)
    List<DocumentChunkEntity> findExactNearestChunks(
        @Param("tenantId") UUID tenantId,
        @Param("queryVector") String queryVector,
        @Param("topK") int topK
    );
}
