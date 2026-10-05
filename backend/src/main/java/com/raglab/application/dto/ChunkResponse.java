package com.raglab.application.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ChunkResponse(
    UUID id,
    UUID documentId,
    UUID tenantId,
    Integer chunkIndex,
    String content,
    Integer tokenCount,
    float[] embedding,
    Map<String, Object> metadata,
    Instant createdAt
) {}
