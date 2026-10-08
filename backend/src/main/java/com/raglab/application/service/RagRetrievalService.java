package com.raglab.application.service;

import com.raglab.application.dto.*;
import com.raglab.domain.entity.DocumentEntity;
import com.raglab.domain.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class RagRetrievalService {

    private final VectorSearchService vectorSearchService;
    private final DocumentRepository documentRepository;

    public RagRetrievalService(VectorSearchService vectorSearchService, DocumentRepository documentRepository) {
        this.vectorSearchService = vectorSearchService;
        this.documentRepository = documentRepository;
    }

    public RetrievalQueryResponse executeQuery(RetrievalQueryRequest request) {
        if (request.tenantId() == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (request.query() == null || request.query().trim().isEmpty()) {
            throw new IllegalArgumentException("query cannot be empty");
        }

        long startTime = System.currentTimeMillis();

        // 1. Vector Search
        float[] queryVector = request.queryVector();
        if (queryVector == null || queryVector.length == 0) {
            // Generate deterministic 768-dim pseudo-embedding from query text if raw vector is omitted
            queryVector = generatePseudoEmbedding(request.query(), 768);
        }

        VectorSearchRequest searchRequest = new VectorSearchRequest(
            request.tenantId(),
            queryVector,
            request.topK(),
            request.minScore(),
            request.documentIds()
        );

        List<VectorSearchResult> searchResults = vectorSearchService.search(searchRequest);
        long retrievalLatencyMs = System.currentTimeMillis() - startTime;

        // 2. Fetch document names for citations
        Map<UUID, String> docNameCache = new HashMap<>();
        List<CitationResponse> citations = new ArrayList<>();
        StringBuilder answerBuilder = new StringBuilder();

        if (searchResults.isEmpty()) {
            answerBuilder.append("The provided documents do not contain sufficient information to answer this question.");
        } else {
            answerBuilder.append("Based on the indexed knowledge base:\n");
            int citationIndex = 1;

            for (VectorSearchResult result : searchResults) {
                String docName = docNameCache.computeIfAbsent(result.documentId(), id ->
                    documentRepository.findById(id)
                        .map(DocumentEntity::getFilename)
                        .orElse("document_" + id.toString().substring(0, 8))
                );

                Integer pageNumber = null;
                if (result.metadata() != null && result.metadata().get("page") instanceof Number num) {
                    pageNumber = num.intValue();
                }

                String snippet = result.content();
                if (snippet.length() > 200) {
                    snippet = snippet.substring(0, 200) + "...";
                }

                double score = result.score() != null ? result.score() : 0.0;

                citations.add(new CitationResponse(
                    citationIndex,
                    result.chunkId(),
                    result.documentId(),
                    docName,
                    pageNumber,
                    snippet,
                    score
                ));

                answerBuilder.append(String.format("- %s [%d]\n", result.content().trim(), citationIndex));
                citationIndex++;
            }
        }

        long totalLatencyMs = System.currentTimeMillis() - startTime;

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("cacheHit", false);
        metrics.put("retrievalLatencyMs", retrievalLatencyMs);
        metrics.put("generationLatencyMs", 0L);
        metrics.put("totalLatencyMs", totalLatencyMs);
        metrics.put("candidateCount", searchResults.size());

        return new RetrievalQueryResponse(
            UUID.randomUUID(),
            answerBuilder.toString().trim(),
            citations,
            metrics
        );
    }

    /**
     * Deterministic, reproducible normalized embedding vector generation from text
     * for testing and standalone local execution.
     */
    public static float[] generatePseudoEmbedding(String text, int dimensions) {
        float[] vector = new float[dimensions];
        if (text == null || text.isEmpty()) {
            return vector;
        }

        // Use multiple hash seeds across text tokens to seed dimensions
        String[] tokens = text.toLowerCase().split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            int hash = token.hashCode();
            int baseIdx = Math.abs(hash) % dimensions;
            vector[baseIdx] += 1.0f / (i + 1);

            int secondaryHash = (hash * 31 + 17) & 0x7FFFFFFF;
            int secIdx = secondaryHash % dimensions;
            vector[secIdx] += 0.5f / (i + 1);
        }

        // Normalize to unit length
        double norm = 0.0;
        for (float v : vector) {
            norm += v * v;
        }
        if (norm > 0.0) {
            float sqrtNorm = (float) Math.sqrt(norm);
            for (int i = 0; i < dimensions; i++) {
                vector[i] /= sqrtNorm;
            }
        } else {
            vector[0] = 1.0f;
        }

        return vector;
    }
}
