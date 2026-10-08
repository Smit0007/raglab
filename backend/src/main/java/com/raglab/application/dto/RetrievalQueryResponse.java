package com.raglab.application.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RetrievalQueryResponse(
    UUID queryId,
    String answer,
    List<CitationResponse> citations,
    Map<String, Object> executionMetrics
) {
}
