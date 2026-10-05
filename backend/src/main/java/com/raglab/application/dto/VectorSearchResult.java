package com.raglab.application.dto;

import java.util.Map;
import java.util.UUID;

public record VectorSearchResult(
    UUID chunkId,
    UUID documentId,
    UUID tenantId,
    Integer chunkIndex,
    String content,
    Integer tokenCount,
    Map<String, Object> metadata,
    Double score
) {}
