package com.raglab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglab.application.dto.*;
import com.raglab.domain.entity.TenantEntity;
import com.raglab.domain.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class Phase4VerticalSliceIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        TenantEntity tenant = tenantRepository.save(new TenantEntity("Phase 4 Tenant", UUID.randomUUID().toString()));
        tenantId = tenant.getId();
    }

    @Test
    @DisplayName("Phase 4 Vertical Slice: Ingest document, ingest chunk with embedding, and perform synchronous RAG query")
    void testSyncRagQueryWorkflow() throws Exception {
        // 1. Ingest a document
        CreateDocumentRequest docReq = new CreateDocumentRequest(
            tenantId,
            "distributed_consensus.md",
            "text/markdown",
            1024L,
            "hash_" + UUID.randomUUID(),
            "/storage/distributed_consensus.md",
            Map.of("category", "systems", "author", "Leslie Lamport")
        );

        String docRespStr = mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(docReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        DocumentResponse docResp = objectMapper.readValue(docRespStr, DocumentResponse.class);

        // 2. Ingest a chunk with known 768-dim embedding
        float[] sampleEmbedding = new float[768];
        sampleEmbedding[0] = 0.85f;
        sampleEmbedding[1] = 0.52f;

        CreateChunkRequest chunkReq = new CreateChunkRequest(
            docResp.id(),
            tenantId,
            0,
            "Raft provides a comprehensible alternative to Paxos for replicated state machine consensus.",
            14,
            sampleEmbedding,
            Map.of("page", 3, "section", "Consensus Algorithms")
        );

        mockMvc.perform(post("/api/v1/chunks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(chunkReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty());

        // 3. Query the vertical slice via POST /api/v1/query
        RetrievalQueryRequest queryReq = new RetrievalQueryRequest(
            tenantId,
            "What is an alternative algorithm for replicated state machines?",
            sampleEmbedding,
            "VECTOR",
            5,
            0.1,
            List.of(docResp.id()),
            "llama3:8b",
            0.1,
            true
        );

        mockMvc.perform(post("/api/v1/query")
                .header("X-Tenant-ID", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(queryReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queryId").isNotEmpty())
            .andExpect(jsonPath("$.answer", containsString("Raft provides a comprehensible alternative")))
            .andExpect(jsonPath("$.citations", hasSize(1)))
            .andExpect(jsonPath("$.citations[0].documentName", is("distributed_consensus.md")))
            .andExpect(jsonPath("$.citations[0].pageNumber", is(3)))
            .andExpect(jsonPath("$.executionMetrics.retrievalLatencyMs").isNumber());
    }

    @Test
    @DisplayName("Phase 4 Tier 1 Evaluation: Run retrieval evaluation benchmark")
    void testTier1RetrievalEvaluationBenchmark() throws Exception {
        RetrievalEvaluationRequest evalReq = new RetrievalEvaluationRequest(
            "golden_docs_v1",
            "VECTOR",
            5,
            true
        );

        mockMvc.perform(post("/api/v1/evaluations/run-retrieval")
                .header("X-Tenant-ID", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(evalReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.runId").isNotEmpty())
            .andExpect(jsonPath("$.recallAt3").isNumber())
            .andExpect(jsonPath("$.recallAt5").isNumber())
            .andExpect(jsonPath("$.mrr").isNumber())
            .andExpect(jsonPath("$.ndcgAt10").isNumber())
            .andExpect(jsonPath("$.hnswRecallVsExact").isNumber())
            .andExpect(jsonPath("$.latencyP95Ms").isNumber());
    }

    @Test
    @DisplayName("CORS: Verify OPTIONS preflight request to /api/v1/query succeeds with appropriate CORS headers")
    void testCorsPreflightOnQueryEndpoint() throws Exception {
        mockMvc.perform(options("/api/v1/query")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type, X-Tenant-ID"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
            .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
            .andExpect(header().string("Access-Control-Allow-Headers", containsString("X-Tenant-ID")))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }
}

