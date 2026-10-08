package com.raglab.application.dto;

import java.util.List;
import java.util.UUID;

public record RetrievalQueryRequest(
    UUID tenantId,
    String query,
    float[] queryVector,
    String retrievalStrategy, // VECTOR, DENSE, etc.
    Integer topK,
    Double minScore,
    List<UUID> documentIds,
    String model,
    Double temperature,
    Boolean useCache
) {
    public RetrievalQueryRequest {
        if (topK == null || topK <= 0) {
            topK = 5;
        }
        if (minScore == null) {
            minScore = 0.0;
        }
        if (retrievalStrategy == null || retrievalStrategy.isBlank()) {
            retrievalStrategy = "VECTOR";
        }
    }
}
