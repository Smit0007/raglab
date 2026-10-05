package com.raglab.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evaluation_results")
public class EvaluationResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String query;

    @Column(name = "ground_truth", columnDefinition = "TEXT")
    private String groundTruth;

    @Column(name = "retrieved_chunk_ids", columnDefinition = "uuid[]")
    private UUID[] retrievedChunkIds = new UUID[0];

    @Column(name = "generated_response", nullable = false, columnDefinition = "TEXT")
    private String generatedResponse;

    @Column(name = "context_relevance", nullable = false)
    private Double contextRelevance = 0.0;

    @Column(nullable = false)
    private Double groundedness = 0.0;

    @Column(name = "answer_relevance", nullable = false)
    private Double answerRelevance = 0.0;

    @Column(name = "execution_time_ms", nullable = false)
    private Long executionTimeMs = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public EvaluationResultEntity() {}

    public EvaluationResultEntity(UUID runId, String query, String groundTruth,
                                  UUID[] retrievedChunkIds, String generatedResponse,
                                  Double contextRelevance, Double groundedness,
                                  Double answerRelevance, Long executionTimeMs) {
        this.runId = runId;
        this.query = query;
        this.groundTruth = groundTruth;
        this.retrievedChunkIds = (retrievedChunkIds != null) ? retrievedChunkIds : new UUID[0];
        this.generatedResponse = generatedResponse;
        this.contextRelevance = (contextRelevance != null) ? contextRelevance : 0.0;
        this.groundedness = (groundedness != null) ? groundedness : 0.0;
        this.answerRelevance = (answerRelevance != null) ? answerRelevance : 0.0;
        this.executionTimeMs = (executionTimeMs != null) ? executionTimeMs : 0L;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getGroundTruth() {
        return groundTruth;
    }

    public void setGroundTruth(String groundTruth) {
        this.groundTruth = groundTruth;
    }

    public UUID[] getRetrievedChunkIds() {
        return retrievedChunkIds;
    }

    public void setRetrievedChunkIds(UUID[] retrievedChunkIds) {
        this.retrievedChunkIds = retrievedChunkIds;
    }

    public String getGeneratedResponse() {
        return generatedResponse;
    }

    public void setGeneratedResponse(String generatedResponse) {
        this.generatedResponse = generatedResponse;
    }

    public Double getContextRelevance() {
        return contextRelevance;
    }

    public void setContextRelevance(Double contextRelevance) {
        this.contextRelevance = contextRelevance;
    }

    public Double getGroundedness() {
        return groundedness;
    }

    public void setGroundedness(Double groundedness) {
        this.groundedness = groundedness;
    }

    public Double getAnswerRelevance() {
        return answerRelevance;
    }

    public void setAnswerRelevance(Double answerRelevance) {
        this.answerRelevance = answerRelevance;
    }

    public Long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(Long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
