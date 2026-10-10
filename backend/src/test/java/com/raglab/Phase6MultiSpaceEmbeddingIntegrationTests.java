package com.raglab;

import com.raglab.application.service.embedding.MultiSpaceEmbeddingService;
import com.raglab.application.service.embedding.VectorRepresentationUtil;
import com.raglab.domain.entity.ChunkEmbeddingsEntity;
import com.raglab.domain.entity.DocumentChunkEntity;
import com.raglab.domain.entity.DocumentEntity;
import com.raglab.domain.entity.TenantEntity;
import com.raglab.domain.repository.ChunkEmbeddingsRepository;
import com.raglab.domain.repository.DocumentChunkRepository;
import com.raglab.domain.repository.DocumentRepository;
import com.raglab.domain.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class Phase6MultiSpaceEmbeddingIntegrationTests {

    @Autowired
    private MultiSpaceEmbeddingService multiSpaceEmbeddingService;

    @Autowired
    private ChunkEmbeddingsRepository chunkEmbeddingsRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private UUID tenantId;
    private DocumentChunkEntity chunk;

    @BeforeEach
    void setUp() {
        TenantEntity tenant = tenantRepository.save(new TenantEntity("Phase 6 Tenant", UUID.randomUUID().toString()));
        tenantId = tenant.getId();

        DocumentEntity doc = documentRepository.save(new DocumentEntity(
            tenantId,
            "multispace_doc.txt",
            "text/plain",
            256L,
            "hash_" + UUID.randomUUID(),
            "/data/multispace_doc.txt",
            new HashMap<>()
        ));

        chunk = documentChunkRepository.save(new DocumentChunkEntity(
            doc.getId(),
            tenantId,
            0,
            "Vector quantization and Matryoshka dimensionality reduction benchmarking.",
            10,
            VectorRepresentationUtil.toVectorString(new float[768]),
            new HashMap<>()
        ));
    }

    @Test
    @DisplayName("VectorRepresentationUtil: MRL truncation and 1-bit quantization unit validation")
    void testVectorRepresentationMath() {
        float[] fullVec = new float[768];
        for (int i = 0; i < fullVec.length; i++) {
            fullVec[i] = (i % 2 == 0) ? 0.5f : -0.5f;
        }

        // 1. Matryoshka truncation to 256
        float[] mrl256 = VectorRepresentationUtil.truncateMatryoshka(fullVec, 256);
        assertNotNull(mrl256);
        assertEquals(256, mrl256.length);

        // Verify normalized unit length
        double normSq = 0.0;
        for (float v : mrl256) {
            normSq += v * v;
        }
        assertEquals(1.0, normSq, 1e-4, "MRL truncated vector must be pre-normalized to unit L2");

        // 2. Binary quantization (sign-based)
        String binStr = VectorRepresentationUtil.quantizeToBinaryBitString(fullVec);
        assertNotNull(binStr);
        assertEquals(768, binStr.length());
        assertEquals('1', binStr.charAt(0));
        assertEquals('0', binStr.charAt(1));

        // 3. Halfvec string format
        String halfStr = VectorRepresentationUtil.toHalfvecString(fullVec);
        assertTrue(halfStr.startsWith("["));
        assertTrue(halfStr.endsWith("]"));
    }

    @Test
    @DisplayName("MultiSpace: Generate, persist, and query all 4 representation spaces (Dense, MRL, Halfvec, Binary)")
    void testMultiSpacePersistenceAndRetrieval() {
        float[] sampleVec = new float[768];
        sampleVec[0] = 0.8f;
        sampleVec[1] = 0.6f;

        List<ChunkEmbeddingsEntity> saved = multiSpaceEmbeddingService.generateAndPersistRepresentations(
            chunk.getId(),
            tenantId,
            sampleVec
        );

        assertNotNull(saved);
        assertEquals(4, saved.size());

        // Verify query by space
        List<ChunkEmbeddingsEntity> denseList = chunkEmbeddingsRepository.findAllByTenantIdAndSpaceId(tenantId, "nomic-embed-v1.5-768-dense");
        assertEquals(1, denseList.size());
        assertEquals(768, denseList.get(0).getDimension());
        assertNotNull(denseList.get(0).getEmbeddingFp32());

        List<ChunkEmbeddingsEntity> mrlList = chunkEmbeddingsRepository.findAllByTenantIdAndSpaceId(tenantId, "nomic-embed-v1.5-256-mrl");
        assertEquals(1, mrlList.size());
        assertEquals(256, mrlList.get(0).getDimension());
        assertNotNull(mrlList.get(0).getEmbeddingFp32());

        List<ChunkEmbeddingsEntity> halfList = chunkEmbeddingsRepository.findAllByTenantIdAndSpaceId(tenantId, "nomic-embed-v1.5-768-half");
        assertEquals(1, halfList.size());
        assertNotNull(halfList.get(0).getEmbeddingHalf());

        List<ChunkEmbeddingsEntity> binList = chunkEmbeddingsRepository.findAllByTenantIdAndSpaceId(tenantId, "nomic-embed-v1.5-768-bin");
        assertEquals(1, binList.size());
        assertNotNull(binList.get(0).getEmbeddingBin());
    }
}
