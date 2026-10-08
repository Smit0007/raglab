package com.raglab.application.dto;

import java.util.UUID;

public record DocumentUploadResponse(
    UUID documentId,
    String filename,
    long fileSize,
    String status,
    Integer pageCount,
    int totalChunks,
    int embeddedChunks,
    long processingDurationMs
) {}
