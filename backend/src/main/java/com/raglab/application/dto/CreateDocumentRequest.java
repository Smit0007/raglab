package com.raglab.application.dto;

import java.util.Map;
import java.util.UUID;

public record CreateDocumentRequest(
    UUID tenantId,
    String filename,
    String contentType,
    Long fileSizeBytes,
    String fileHashSha256,
    String storagePath,
    Map<String, Object> metadata
) {}
