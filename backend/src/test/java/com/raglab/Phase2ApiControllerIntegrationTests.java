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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class Phase2ApiControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        TenantEntity tenant = tenantRepository.save(new TenantEntity("API Test Tenant", UUID.randomUUID().toString()));
        tenantId = tenant.getId();
    }

    @Test
    @DisplayName("REST API: Create document, update metadata, create chunk with embedding, and perform vector search")
    void testFullPhase2RestApiWorkflow() throws Exception {
        // 1. Create document via POST /api/v1/documents
        CreateDocumentRequest docReq = new CreateDocumentRequest(
            tenantId,
            "neural_search_spec.pdf",
            "application/pdf",
            102400L,
            "hash_spec_" + UUID.randomUUID(),
            "/storage/neural_search_spec.pdf",
            Map.of("department", "AI Research", "confidentiality", "internal")
        );

        String docResponseStr = mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(docReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.filename").value("neural_search_spec.pdf"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.metadata.department").value("AI Research"))
            .andReturn().getResponse().getContentAsString();

        DocumentResponse docRes = objectMapper.readValue(docResponseStr, DocumentResponse.class);
        UUID docId = docRes.id();

        // 2. Patch document metadata via PATCH /api/v1/documents/{id}/metadata
        mockMvc.perform(patch("/api/v1/documents/{id}/metadata", docId)
                .header("X-Tenant-ID", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("department", "AI Research", "version", "2.0"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metadata.version").value("2.0"));

        // 3. Create document chunks via POST /api/v1/chunks
        float[] vectorA = new float[768];
        vectorA[0] = 0.8f;
        vectorA[1] = 0.6f;

        CreateChunkRequest chunkReq1 = new CreateChunkRequest(
            docId,
            tenantId,
            0,
            "Dense vector retrieval uses high-dimensional embeddings and cosine similarity.",
            11,
            vectorA,
            Map.of("section", "Introduction", "page", 1)
        );

        mockMvc.perform(post("/api/v1/chunks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(chunkReq1)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.chunkIndex").value(0))
            .andExpect(jsonPath("$.embedding", hasSize(768)));

        float[] vectorB = new float[768];
        vectorB[50] = 1.0f; // Orthogonal vector

        CreateChunkRequest chunkReq2 = new CreateChunkRequest(
            docId,
            tenantId,
            1,
            "Lexical BM25 search uses term frequency and inverted indices.",
            10,
            vectorB,
            Map.of("section", "BM25 Baseline", "page", 2)
        );

        mockMvc.perform(post("/api/v1/chunks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(chunkReq2)))
            .andExpect(status().isCreated());

        // 4. Perform vector search via POST /api/v1/search/vector
        float[] queryVector = new float[768];
        queryVector[0] = 0.82f;
        queryVector[1] = 0.58f;

        VectorSearchRequest searchReq = new VectorSearchRequest(
            tenantId,
            queryVector,
            5,
            0.5,
            List.of(docId)
        );

        mockMvc.perform(post("/api/v1/search/vector")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(searchReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].content").value(containsString("Dense vector retrieval")))
            .andExpect(jsonPath("$[0].score", greaterThan(0.95)));
    }
}
