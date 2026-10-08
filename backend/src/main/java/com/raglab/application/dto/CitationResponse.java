package com.raglab.application.dto;

import java.util.UUID;

public record CitationResponse(
    int citationIndex,
    UUID chunkId,
    UUID documentId,
    String documentName,
    Integer pageNumber,
    String snippet,
    double score
) {
}
