package com.raglab;

import com.raglab.application.dto.RetrievalQueryRequest;
import com.raglab.application.dto.RetrievalQueryResponse;
import com.raglab.application.service.RagRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
class RetrievalEvaluationSmokeTest {

    @Autowired
    private RagRetrievalService ragRetrievalService;

    @Autowired
    private com.raglab.domain.repository.TenantRepository tenantRepository;

    @Autowired
    private com.raglab.domain.repository.DocumentRepository documentRepository;

    @Autowired
    private com.raglab.domain.repository.DocumentChunkRepository documentChunkRepository;

    private java.util.UUID tenantId;

    @BeforeEach
    void setUp() {
        // Save tenant first to respect foreign key constraint
        com.raglab.domain.entity.TenantEntity tenant = tenantRepository.save(new com.raglab.domain.entity.TenantEntity("Smoke Test Tenant", UUID.randomUUID().toString()));
        tenantId = tenant.getId();

        com.raglab.domain.entity.DocumentEntity doc = documentRepository.save(new com.raglab.domain.entity.DocumentEntity(
            tenantId,
            "quantum_smoke.txt",
            "text/plain",
            128L,
            "hash_" + UUID.randomUUID(),
            "/data/quantum_smoke.txt",
            new java.util.HashMap<>()
        ));

        float[] sampleEmbedding = RagRetrievalService.generatePseudoEmbedding("How does quantum computing achieve computational acceleration? superposition and entanglement", 768);
        documentChunkRepository.save(new com.raglab.domain.entity.DocumentChunkEntity(
            doc.getId(),
            tenantId,
            0,
            "Quantum computing achieves computational acceleration via superposition and entanglement principles.",
            12,
            new com.pgvector.PGvector(sampleEmbedding).getValue(),
            new java.util.HashMap<>()
        ));
    }

    @Test
    @DisplayName("Answerable query returns relevant chunk")
    void testAnswerableQuery() {
        RetrievalQueryRequest request = new RetrievalQueryRequest(
                tenantId,
                "How does quantum computing achieve computational acceleration?",
                null, // no pre‑computed vector
                "VECTOR",
                5,
                0.0,
                null,
                "llama3:8b",
                0.1,
                true
        );
        RetrievalQueryResponse resp = ragRetrievalService.executeQuery(request);
        assertNotNull(resp);
        assertFalse(resp.citations().isEmpty(), "At least one citation expected");
        // The top citation snippet should contain the word "superposition"
        String topSnippet = resp.citations().get(0).snippet().toLowerCase();
        assertTrue(topSnippet.contains("superposition"), "Top snippet should mention superposition");
    }

    @Test
    @DisplayName("Unanswerable query returns fallback message")
    void testUnanswerableQuery() {
        RetrievalQueryRequest request = new RetrievalQueryRequest(
                tenantId,
                "What is the weather on Mars tomorrow?",
                null,
                "VECTOR",
                5,
                0.99, // High threshold for unrelated query
                null,
                "llama3:8b",
                0.1,
                true
        );
        RetrievalQueryResponse resp = ragRetrievalService.executeQuery(request);
        assertNotNull(resp);
        assertTrue(resp.citations().isEmpty(), "No citations expected for unrelated question");
        assertTrue(resp.answer().contains("do not contain sufficient information"), "Fallback sentence expected");
    }
}
