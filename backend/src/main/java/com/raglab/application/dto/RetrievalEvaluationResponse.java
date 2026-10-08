package com.raglab.application.dto;

import java.util.UUID;

public record RetrievalEvaluationResponse(
    UUID runId,
    double recallAt3,
    double recallAt5,
    double mrr,
    double ndcgAt10,
    double hnswRecallVsExact,
    double latencyP95Ms
) {
}
