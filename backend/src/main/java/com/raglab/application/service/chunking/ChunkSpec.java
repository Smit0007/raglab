package com.raglab.application.service.chunking;

import java.util.Map;

public record ChunkSpec(
    int chunkIndex,
    String content,
    int tokenCount,
    Integer pageNumber,
    Map<String, Object> metadata
) {}
