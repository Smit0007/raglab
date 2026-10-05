package com.raglab.application.service;

import com.pgvector.PGvector;
import com.raglab.application.dto.VectorSearchRequest;
import com.raglab.application.dto.VectorSearchResult;
import com.raglab.domain.entity.DocumentChunkEntity;
import com.raglab.domain.repository.DocumentChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class VectorSearchService {

    private final DocumentChunkRepository chunkRepository;

    public VectorSearchService(DocumentChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    public List<VectorSearchResult> search(VectorSearchRequest request) {
        if (request.tenantId() == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (request.queryVector() == null || request.queryVector().length == 0) {
            throw new IllegalArgumentException("queryVector is required");
        }

        int topK = (request.topK() != null && request.topK() > 0) ? request.topK() : 5;
        double minScore = (request.minScore() != null) ? request.minScore() : 0.0;
        String vectorString = new PGvector(request.queryVector()).getValue();

        List<DocumentChunkEntity> entities;
        if (request.documentIds() != null && !request.documentIds().isEmpty()) {
            entities = chunkRepository.findSimilarChunksInDocuments(
                request.tenantId(),
                request.documentIds(),
                vectorString,
                topK,
                minScore
            );
        } else {
            entities = chunkRepository.findSimilarChunks(
                request.tenantId(),
                vectorString,
                topK,
                minScore
            );
        }

        return entities.stream().map(entity -> {
            Double calculatedScore = null;
            if (entity.getEmbedding() != null) {
                try {
                    float[] chunkEmb = new PGvector(entity.getEmbedding()).toArray();
                    calculatedScore = computeCosineSimilarity(request.queryVector(), chunkEmb);
                } catch (Exception ignored) {
                }
            }

            return new VectorSearchResult(
                entity.getId(),
                entity.getDocumentId(),
                entity.getTenantId(),
                entity.getChunkIndex(),
                entity.getContent(),
                entity.getTokenCount(),
                entity.getMetadata(),
                calculatedScore
            );
        }).toList();
    }

    private double computeCosineSimilarity(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length != v2.length) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
