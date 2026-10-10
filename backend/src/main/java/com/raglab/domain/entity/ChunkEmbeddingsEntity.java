package com.raglab.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chunk_embeddings")
public class ChunkEmbeddingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "chunk_id", nullable = false)
    private UUID chunkId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "space_id", length = 64, nullable = false)
    private String spaceId;

    @Column(name = "dimension", nullable = false)
    private int dimension;

    @Column(name = "representation_type", length = 32, nullable = false)
    private String representationType;

    // Vector stored as native PGvector string or text
    @Column(name = "embedding_fp32", columnDefinition = "vector")
    @org.hibernate.annotations.ColumnTransformer(
        write = "?::vector",
        read = "embedding_fp32::text"
    )
    private String embeddingFp32;

    @Column(name = "embedding_half", columnDefinition = "halfvec")
    @org.hibernate.annotations.ColumnTransformer(
        write = "?::halfvec",
        read = "embedding_half::text"
    )
    private String embeddingHalf;

    @Column(name = "embedding_bin", columnDefinition = "text")
    private String embeddingBin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ChunkEmbeddingsEntity() {}

    public ChunkEmbeddingsEntity(UUID chunkId, UUID tenantId, String spaceId, int dimension,
                                 String representationType, String embeddingFp32, String embeddingHalf, String embeddingBin) {
        this.chunkId = chunkId;
        this.tenantId = tenantId;
        this.spaceId = spaceId;
        this.dimension = dimension;
        this.representationType = representationType;
        this.embeddingFp32 = embeddingFp32;
        this.embeddingHalf = embeddingHalf;
        this.embeddingBin = embeddingBin;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getChunkId() { return chunkId; }
    public void setChunkId(UUID chunkId) { this.chunkId = chunkId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSpaceId() { return spaceId; }
    public void setSpaceId(String spaceId) { this.spaceId = spaceId; }
    public int getDimension() { return dimension; }
    public void setDimension(int dimension) { this.dimension = dimension; }
    public String getRepresentationType() { return representationType; }
    public void setRepresentationType(String representationType) { this.representationType = representationType; }
    public String getEmbeddingFp32() { return embeddingFp32; }
    public void setEmbeddingFp32(String embeddingFp32) { this.embeddingFp32 = embeddingFp32; }
    public String getEmbeddingHalf() { return embeddingHalf; }
    public void setEmbeddingHalf(String embeddingHalf) { this.embeddingHalf = embeddingHalf; }
    public String getEmbeddingBin() { return embeddingBin; }
    public void setEmbeddingBin(String embeddingBin) { this.embeddingBin = embeddingBin; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
