package com.raglab.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "embedding_spaces")
public class EmbeddingSpaceEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "model_name", length = 128, nullable = false)
    private String modelName;

    @Column(name = "model_revision", length = 64, nullable = false)
    private String modelRevision;

    @Column(name = "dimension", nullable = false)
    private int dimension;

    @Column(name = "representation_type", length = 32, nullable = false)
    private String representationType;

    @Column(name = "distance_metric", length = 32, nullable = false)
    private String distanceMetric;

    @Column(name = "normalization_policy", length = 32, nullable = false)
    private String normalizationPolicy;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public EmbeddingSpaceEntity() {}

    public EmbeddingSpaceEntity(String id, String modelName, String modelRevision, int dimension,
                                String representationType, String distanceMetric, String normalizationPolicy, boolean active) {
        this.id = id;
        this.modelName = modelName;
        this.modelRevision = modelRevision;
        this.dimension = dimension;
        this.representationType = representationType;
        this.distanceMetric = distanceMetric;
        this.normalizationPolicy = normalizationPolicy;
        this.active = active;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getModelRevision() { return modelRevision; }
    public void setModelRevision(String modelRevision) { this.modelRevision = modelRevision; }
    public int getDimension() { return dimension; }
    public void setDimension(int dimension) { this.dimension = dimension; }
    public String getRepresentationType() { return representationType; }
    public void setRepresentationType(String representationType) { this.representationType = representationType; }
    public String getDistanceMetric() { return distanceMetric; }
    public void setDistanceMetric(String distanceMetric) { this.distanceMetric = distanceMetric; }
    public String getNormalizationPolicy() { return normalizationPolicy; }
    public void setNormalizationPolicy(String normalizationPolicy) { this.normalizationPolicy = normalizationPolicy; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

