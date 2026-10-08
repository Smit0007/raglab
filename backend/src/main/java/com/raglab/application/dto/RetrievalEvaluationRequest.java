package com.raglab.application.dto;

public record RetrievalEvaluationRequest(
    String datasetId,
    String strategy,
    Integer topK,
    Boolean compareExact
) {
    public RetrievalEvaluationRequest {
        if (topK == null || topK <= 0) {
            topK = 5;
        }
        if (strategy == null || strategy.isBlank()) {
            strategy = "VECTOR";
        }
        if (compareExact == null) {
            compareExact = true;
        }
    }
}
