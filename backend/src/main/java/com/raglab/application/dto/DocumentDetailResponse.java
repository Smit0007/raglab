package com.raglab.application.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DocumentDetailResponse(
    UUID id,
    UUID tenantId,
    String filename,
    String contentType,
    Long fileSizeBytes,
    String status,
    String errorMessage,
    Map<String, Object> metadata,
    Integer pageCount,
    int totalChunks,
    int embeddedChunks,
    Instant createdAt,
    Instant updatedAt
) {}
