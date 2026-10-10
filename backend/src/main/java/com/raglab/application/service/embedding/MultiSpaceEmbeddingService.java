package com.raglab.application.service.embedding;

import com.raglab.domain.entity.ChunkEmbeddingsEntity;
import com.raglab.domain.entity.EmbeddingSpaceEntity;
import com.raglab.domain.repository.ChunkEmbeddingsRepository;
import com.raglab.domain.repository.EmbeddingSpaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class MultiSpaceEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(MultiSpaceEmbeddingService.class);

    private final EmbeddingSpaceRepository embeddingSpaceRepository;
    private final ChunkEmbeddingsRepository chunkEmbeddingsRepository;

    public MultiSpaceEmbeddingService(
        EmbeddingSpaceRepository embeddingSpaceRepository,
        ChunkEmbeddingsRepository chunkEmbeddingsRepository
    ) {
        this.embeddingSpaceRepository = embeddingSpaceRepository;
        this.chunkEmbeddingsRepository = chunkEmbeddingsRepository;
    }

    @Transactional
    public List<ChunkEmbeddingsEntity> generateAndPersistRepresentations(
        UUID chunkId,
        UUID tenantId,
        float[] base768Vector
    ) {
        if (base768Vector == null || base768Vector.length != 768) {
            throw new IllegalArgumentException("Base vector must be 768 dimensions");
        }

        List<ChunkEmbeddingsEntity> list = new ArrayList<>();

        // 1. Full precision dense representation (nomic-embed-v1.5-768-dense)
        String denseVecStr = VectorRepresentationUtil.toVectorString(base768Vector);
        ChunkEmbeddingsEntity dense = new ChunkEmbeddingsEntity(
            chunkId, tenantId, "nomic-embed-v1.5-768-dense", 768, "FLOAT32_DENSE",
            denseVecStr, null, null
        );
        list.add(dense);

        // 2. Matryoshka 256 representation (nomic-embed-v1.5-256-mrl)
        float[] mrl256 = VectorRepresentationUtil.truncateMatryoshka(base768Vector, 256);
        String mrlVecStr = VectorRepresentationUtil.toVectorString(mrl256);
        ChunkEmbeddingsEntity mrl = new ChunkEmbeddingsEntity(
            chunkId, tenantId, "nomic-embed-v1.5-256-mrl", 256, "MRL_TRUNCATED",
            mrlVecStr, null, null
        );
        list.add(mrl);

        // 3. Half-precision halfvec representation (nomic-embed-v1.5-768-half)
        String halfStr = VectorRepresentationUtil.toHalfvecString(base768Vector);
        ChunkEmbeddingsEntity half = new ChunkEmbeddingsEntity(
            chunkId, tenantId, "nomic-embed-v1.5-768-half", 768, "FLOAT16_HALFVEC",
            null, halfStr, null
        );
        list.add(half);

        // 4. Binary quantized representation (nomic-embed-v1.5-768-bin)
        String binStr = VectorRepresentationUtil.quantizeToBinaryBitString(base768Vector);
        ChunkEmbeddingsEntity bin = new ChunkEmbeddingsEntity(
            chunkId, tenantId, "nomic-embed-v1.5-768-bin", 768, "BINARY_QUANTIZED",
            null, null, binStr
        );
        list.add(bin);

        return chunkEmbeddingsRepository.saveAll(list);
    }

    public Optional<EmbeddingSpaceEntity> getSpace(String spaceId) {
        return embeddingSpaceRepository.findByIdAndActiveTrue(spaceId);
    }
}

