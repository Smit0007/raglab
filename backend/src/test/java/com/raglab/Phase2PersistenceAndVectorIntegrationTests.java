package com.raglab;

import com.raglab.application.dto.*;
import com.raglab.application.service.DocumentChunkService;
import com.raglab.application.service.DocumentService;
import com.raglab.application.service.VectorSearchService;
import com.raglab.domain.entity.*;
import com.raglab.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class Phase2PersistenceAndVectorIntegrationTests {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ExperimentRepository experimentRepository;

    @Autowired
    private ExperimentRunRepository experimentRunRepository;

    @Autowired
    private EvaluationResultRepository evaluationResultRepository;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentChunkService documentChunkService;

    @Autowired
    private VectorSearchService vectorSearchService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        TenantEntity tenant = tenantRepository.save(new TenantEntity("Phase 2 Test Tenant", UUID.randomUUID().toString()));
        tenantId = tenant.getId();
    }

    @Test
    @DisplayName("Verify persistence of all 9 Phase 2 required domain entities")
    void testPersistenceOfAllDomainEntities() {
        // 1. User
        UserEntity user = userRepository.save(new UserEntity(tenantId, "testuser@raglab.dev", "Test User", "ADMIN"));
        assertThat(user.getId()).isNotNull();

        // 2. Document
        DocumentEntity doc = documentRepository.save(new DocumentEntity(
            tenantId, "architecture.pdf", "application/pdf", 102400L,
            UUID.randomUUID().toString(), "/storage/architecture.pdf",
            Map.of("author", "DeepMind Team", "category", "whitepaper")
        ));
        assertThat(doc.getId()).isNotNull();

        // 3. DocumentVersion
        DocumentVersionEntity version = documentVersionRepository.save(new DocumentVersionEntity(
            doc.getId(), 1, doc.getFileHashSha256(), doc.getStoragePath(), doc.getFileSizeBytes(),
            user.getId(), Map.of("changelog", "Initial version")
        ));
        assertThat(version.getId()).isNotNull();

        // 4. DocumentChunk
        DocumentChunkEntity chunk = documentChunkRepository.save(new DocumentChunkEntity(
            doc.getId(), tenantId, 0, "RAGLab is a retrieval-augmented generation experimentation platform.",
            12, null, Map.of("page", 1)
        ));
        assertThat(chunk.getId()).isNotNull();

        // 5. Conversation
        ConversationEntity conv = conversationRepository.save(new ConversationEntity(
            tenantId, user.getId(), "Chat on Architecture", Map.of("sessionType", "interactive")
        ));
        assertThat(conv.getId()).isNotNull();

        // 6. Message
        MessageEntity msg = messageRepository.save(new MessageEntity(
            conv.getId(), "USER", "How does pgvector work in RAGLab?", "[]", Map.of("client", "web")
        ));
        assertThat(msg.getId()).isNotNull();

        // 7. Experiment
        ExperimentEntity exp = experimentRepository.save(new ExperimentEntity(
            tenantId, "HNSW vs IVFFlat Benchmark", "Evaluate retrieval latency",
            "finance_qa", Map.of("topK", 5)
        ));
        assertThat(exp.getId()).isNotNull();

        // 8. ExperimentRun
        ExperimentRunEntity run = experimentRunRepository.save(new ExperimentRunEntity(
            exp.getId(), tenantId, "Run 1 - HNSW Cosine", "VECTOR_HNSW", "llama3:8b"
        ));
        assertThat(run.getId()).isNotNull();

        // 9. EvaluationResult
        EvaluationResultEntity evalResult = evaluationResultRepository.save(new EvaluationResultEntity(
            run.getId(), "What is RAGLab?", "RAGLab is a research-to-production RAG platform.",
            new UUID[]{chunk.getId()}, "RAGLab is a RAG platform.",
            0.95, 1.0, 0.92, 450L
        ));
        assertThat(evalResult.getId()).isNotNull();
    }

    @Test
    @DisplayName("Verify document and chunk service workflows with metadata and embedding")
    void testDocumentAndChunkServiceWorkflow() {
        // 1. Create document via Service
        CreateDocumentRequest docReq = new CreateDocumentRequest(
            tenantId, "q3_financial_report.pdf", "application/pdf", 204800L,
            "hash_q3_report_" + UUID.randomUUID(), "/storage/q3.pdf",
            Map.of("department", "Finance", "year", 2026)
        );
        DocumentResponse docRes = documentService.createDocument(docReq);
        assertThat(docRes.id()).isNotNull();
        assertThat(docRes.status()).isEqualTo("PENDING");
        assertThat(docRes.metadata()).containsEntry("year", 2026);

        // 2. Create chunks with embeddings
        float[] vectorA = create768DimensionVector(1.0f);
        CreateChunkRequest chunkReq1 = new CreateChunkRequest(
            docRes.id(), tenantId, 0, "Quarterly net revenue grew by 24 percent year over year.",
            10, vectorA, Map.of("section", "Revenue", "page", 3)
        );
        ChunkResponse chunkRes1 = documentChunkService.createChunk(chunkReq1);
        assertThat(chunkRes1.id()).isNotNull();
        assertThat(chunkRes1.embedding()).isNotNull();
        assertThat(chunkRes1.embedding().length).isEqualTo(768);

        float[] vectorB = create768DimensionVector(0.0f);
        CreateChunkRequest chunkReq2 = new CreateChunkRequest(
            docRes.id(), tenantId, 1, "Operating expenses remained flat due to cloud optimization.",
            9, vectorB, Map.of("section", "Expenses", "page", 4)
        );
        ChunkResponse chunkRes2 = documentChunkService.createChunk(chunkReq2);
        assertThat(chunkRes2.id()).isNotNull();

        // 3. List chunks by Document ID
        List<ChunkResponse> chunks = documentChunkService.getChunksByDocumentId(docRes.id());
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).chunkIndex()).isEqualTo(0);
        assertThat(chunks.get(1).chunkIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("Verify pgvector cosine similarity search against PostgreSQL")
    void testPgVectorCosineSimilaritySearch() {
        // Create document
        DocumentResponse doc = documentService.createDocument(new CreateDocumentRequest(
            tenantId, "ai_safety_guidelines.pdf", "application/pdf", 50000L,
            "hash_safety_" + UUID.randomUUID(), "/storage/safety.pdf",
            Map.of("topic", "AI Safety")
        ));

        // Create 3 chunks with distinct vectors (768 dimensions)
        float[] targetVector = new float[768];
        targetVector[0] = 1.0f; // Unit vector in dimension 0
        targetVector[1] = 0.5f;

        float[] nearTargetVector = new float[768];
        nearTargetVector[0] = 0.95f; // Very close to targetVector
        nearTargetVector[1] = 0.48f;

        float[] orthogonalVector = new float[768];
        orthogonalVector[10] = 1.0f; // Orthogonal direction

        documentChunkService.createChunk(new CreateChunkRequest(
            doc.id(), tenantId, 0, "Primary safety policy regarding alignment.",
            6, nearTargetVector, Map.of("importance", "high")
        ));

        documentChunkService.createChunk(new CreateChunkRequest(
            doc.id(), tenantId, 1, "Orthogonal unrelated notes on hardware maintenance.",
            7, orthogonalVector, Map.of("importance", "low")
        ));

        // Perform vector search
        VectorSearchRequest searchReq = new VectorSearchRequest(
            tenantId,
            targetVector,
            2,
            0.5,
            List.of(doc.id())
        );

        List<VectorSearchResult> results = vectorSearchService.search(searchReq);

        assertThat(results).isNotEmpty();
        // The nearest chunk should be the first result with high similarity score (> 0.9)
        assertThat(results.get(0).content()).contains("Primary safety policy");
        assertThat(results.get(0).score()).isGreaterThan(0.9);
    }

    private float[] create768DimensionVector(float initialValue) {
        float[] v = new float[768];
        for (int i = 0; i < 768; i++) {
            v[i] = initialValue + (float) (i * 0.001);
        }
        return v;
    }
}
