package com.raglab.domain.entity;

import com.raglab.infrastructure.persistence.converter.JsonMapConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "experiment_runs")
public class ExperimentRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "experiment_id", nullable = false)
    private UUID experimentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "retrieval_strategy", nullable = false, length = 64)
    private String retrievalStrategy;

    @Column(name = "llm_model", nullable = false, length = 128)
    private String llmModel;

    @Column(name = "context_relevance_score")
    private Double contextRelevanceScore;

    @Column(name = "groundedness_score")
    private Double groundednessScore;

    @Column(name = "answer_relevance_score")
    private Double answerRelevanceScore;

    @Column(name = "latency_p95_ms")
    private Double latencyP95Ms;

    @Column(name = "total_queries", nullable = false)
    private Integer totalQueries = 0;

    @Convert(converter = JsonMapConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics_summary", columnDefinition = "jsonb")
    private Map<String, Object> metricsSummary = new HashMap<>();

    @Column(nullable = false, length = 32)
    private String status = "RUNNING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    public ExperimentRunEntity() {}

    public ExperimentRunEntity(UUID experimentId, UUID tenantId, String name,
                               String retrievalStrategy, String llmModel) {
        this.experimentId = experimentId;
        this.tenantId = tenantId;
        this.name = name;
        this.retrievalStrategy = retrievalStrategy;
        this.llmModel = llmModel;
        this.status = "RUNNING";
        this.totalQueries = 0;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(UUID experimentId) {
        this.experimentId = experimentId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRetrievalStrategy() {
        return retrievalStrategy;
    }

    public void setRetrievalStrategy(String retrievalStrategy) {
        this.retrievalStrategy = retrievalStrategy;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public Double getContextRelevanceScore() {
        return contextRelevanceScore;
    }

    public void setContextRelevanceScore(Double contextRelevanceScore) {
        this.contextRelevanceScore = contextRelevanceScore;
    }

    public Double getGroundednessScore() {
        return groundednessScore;
    }

    public void setGroundednessScore(Double groundednessScore) {
        this.groundednessScore = groundednessScore;
    }

    public Double getAnswerRelevanceScore() {
        return answerRelevanceScore;
    }

    public void setAnswerRelevanceScore(Double answerRelevanceScore) {
        this.answerRelevanceScore = answerRelevanceScore;
    }

    public Double getLatencyP95Ms() {
        return latencyP95Ms;
    }

    public void setLatencyP95Ms(Double latencyP95Ms) {
        this.latencyP95Ms = latencyP95Ms;
    }

    public Integer getTotalQueries() {
        return totalQueries;
    }

    public void setTotalQueries(Integer totalQueries) {
        this.totalQueries = totalQueries;
    }

    public Map<String, Object> getMetricsSummary() {
        return metricsSummary;
    }

    public void setMetricsSummary(Map<String, Object> metricsSummary) {
        this.metricsSummary = metricsSummary;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
