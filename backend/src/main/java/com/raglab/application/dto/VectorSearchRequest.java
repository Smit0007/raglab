package com.raglab.application.dto;

import java.util.List;
import java.util.UUID;

public record VectorSearchRequest(
    UUID tenantId,
    float[] queryVector,
    Integer topK,
    Double minScore,
    List<UUID> documentIds
) {}
