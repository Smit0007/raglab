package com.raglab.application.dto;

import java.util.Map;
import java.util.UUID;

public record DocumentIngestionJobMessage(
    String jobId,
    UUID documentId,
    UUID tenantId,
    String filename,
    String contentType,
    long fileSizeBytes,
    String sourcePath,
    Map<String, Object> metadata
) {}

