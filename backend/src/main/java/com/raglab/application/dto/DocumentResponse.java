package com.raglab.application.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DocumentResponse(
    UUID id,
    UUID tenantId,
    String filename,
    String contentType,
    Long fileSizeBytes,
    String fileHashSha256,
    String storagePath,
    String status,
    String errorMessage,
    Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt
) {}
