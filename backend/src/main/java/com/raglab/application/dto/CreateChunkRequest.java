package com.raglab.application.dto;

import java.util.Map;
import java.util.UUID;

public record CreateChunkRequest(
    UUID documentId,
    UUID tenantId,
    Integer chunkIndex,
    String content,
    Integer tokenCount,
    float[] embedding,
    Map<String, Object> metadata
) {}
