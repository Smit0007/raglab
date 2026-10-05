package com.raglab.application.service;

import com.pgvector.PGvector;
import com.raglab.application.dto.ChunkResponse;
import com.raglab.application.dto.CreateChunkRequest;
import com.raglab.domain.entity.DocumentChunkEntity;
import com.raglab.domain.repository.DocumentChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class DocumentChunkService {

    private final DocumentChunkRepository chunkRepository;

    public DocumentChunkService(DocumentChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    public ChunkResponse createChunk(CreateChunkRequest request) {
        if (request.documentId() == null) {
            throw new IllegalArgumentException("documentId is required");
        }
        if (request.tenantId() == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new IllegalArgumentException("content is required");
        }

        String embeddingString = null;
        if (request.embedding() != null) {
            embeddingString = new PGvector(request.embedding()).getValue();
        }

        DocumentChunkEntity entity = new DocumentChunkEntity(
            request.documentId(),
            request.tenantId(),
            request.chunkIndex() != null ? request.chunkIndex() : 0,
            request.content(),
            request.tokenCount() != null ? request.tokenCount() : 0,
            embeddingString,
            request.metadata()
        );

        DocumentChunkEntity saved = chunkRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Optional<ChunkResponse> getChunk(UUID chunkId) {
        return chunkRepository.findById(chunkId).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ChunkResponse> getChunksByDocumentId(UUID documentId) {
        return chunkRepository.findAllByDocumentIdOrderByChunkIndexAsc(documentId).stream()
            .map(this::toResponse)
            .toList();
    }

    public ChunkResponse updateEmbedding(UUID chunkId, float[] embedding) {
        DocumentChunkEntity entity = chunkRepository.findById(chunkId)
            .orElseThrow(() -> new IllegalArgumentException("Chunk not found with ID: " + chunkId));

        if (embedding != null) {
            entity.setEmbedding(new PGvector(embedding).getValue());
        } else {
            entity.setEmbedding(null);
        }

        return toResponse(chunkRepository.save(entity));
    }

    public void deleteChunksByDocumentId(UUID documentId) {
        chunkRepository.deleteAllByDocumentId(documentId);
    }

    private ChunkResponse toResponse(DocumentChunkEntity entity) {
        float[] embeddingArr = null;
        if (entity.getEmbedding() != null && !entity.getEmbedding().isBlank()) {
            try {
                embeddingArr = new PGvector(entity.getEmbedding()).toArray();
            } catch (Exception ignored) {
            }
        }

        return new ChunkResponse(
            entity.getId(),
            entity.getDocumentId(),
            entity.getTenantId(),
            entity.getChunkIndex(),
            entity.getContent(),
            entity.getTokenCount(),
            embeddingArr,
            entity.getMetadata(),
            entity.getCreatedAt()
        );
    }
}
