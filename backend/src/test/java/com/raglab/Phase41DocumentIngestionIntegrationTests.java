package com.raglab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglab.application.dto.*;
import com.raglab.domain.entity.TenantEntity;
import com.raglab.domain.repository.TenantRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class Phase41DocumentIngestionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    private UUID tenant1Id;
    private UUID tenant2Id;

    @BeforeEach
    void setUp() {
        TenantEntity t1 = tenantRepository.save(new TenantEntity("Tenant Alpha", UUID.randomUUID().toString()));
        TenantEntity t2 = tenantRepository.save(new TenantEntity("Tenant Beta", UUID.randomUUID().toString()));
        tenant1Id = t1.getId();
        tenant2Id = t2.getId();
    }

    @Test
    @DisplayName("End-to-End: Ingest PDF, verify chunks and embeddings in PostgreSQL, and retrieve with citations")
    void testEndToEndDocumentIngestionAndRetrieval() throws Exception {
        // 1. Generate a real PDF in memory
        byte[] pdfBytes = createSamplePdf("Quantum computing utilizes superposition and entanglement for computational acceleration.");

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "quantum_overview.pdf",
            "application/pdf",
            pdfBytes
        );

        // 2. Upload via POST /api/v1/documents/upload
        String uploadRespStr = mockMvc.perform(multipart("/api/v1/documents/upload")
                .file(file)
                .header("X-Tenant-ID", tenant1Id.toString()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.documentId").isNotEmpty())
            .andExpect(jsonPath("$.filename", is("quantum_overview.pdf")))
            .andExpect(jsonPath("$.status", is("COMPLETED")))
            .andExpect(jsonPath("$.pageCount", is(1)))
            .andExpect(jsonPath("$.totalChunks", greaterThan(0)))
            .andExpect(jsonPath("$.embeddedChunks", greaterThan(0)))
            .andExpect(jsonPath("$.processingDurationMs", greaterThan(0)))
            .andReturn().getResponse().getContentAsString();

        DocumentUploadResponse uploadResp = objectMapper.readValue(uploadRespStr, DocumentUploadResponse.class);
        UUID docId = uploadResp.documentId();

        // 3. Verify GET /api/v1/documents/{id}
        mockMvc.perform(get("/api/v1/documents/" + docId)
                .header("X-Tenant-ID", tenant1Id.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(docId.toString())))
            .andExpect(jsonPath("$.status", is("COMPLETED")))
            .andExpect(jsonPath("$.totalChunks", greaterThan(0)));

        // 4. Verify Tenant Isolation on Document Details
        mockMvc.perform(get("/api/v1/documents/" + docId)
                .header("X-Tenant-ID", tenant2Id.toString()))
            .andExpect(status().isNotFound());

        // 5. Query the ingested document via POST /api/v1/query
        RetrievalQueryRequest queryReq = new RetrievalQueryRequest(
            tenant1Id,
            "How does quantum computing achieve computational acceleration?",
            null,
            "VECTOR",
            3,
            0.0,
            null,
            "llama3:8b",
            0.1,
            true
        );

        mockMvc.perform(post("/api/v1/query")
                .header("X-Tenant-ID", tenant1Id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(queryReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queryId").isNotEmpty())
            .andExpect(jsonPath("$.citations", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$.citations[0].documentName", is("quantum_overview.pdf")))
            .andExpect(jsonPath("$.citations[0].snippet", containsString("superposition and entanglement")))
            .andExpect(jsonPath("$.executionMetrics.candidateCount", greaterThan(0)));

        // 6. Verify Tenant Isolation on Query (Tenant Beta cannot retrieve Tenant Alpha's document)
        RetrievalQueryRequest queryReqTenant2 = new RetrievalQueryRequest(
            tenant2Id,
            "How does quantum computing achieve computational acceleration?",
            null,
            "VECTOR",
            3,
            0.0,
            null,
            "llama3:8b",
            0.1,
            true
        );

        mockMvc.perform(post("/api/v1/query")
                .header("X-Tenant-ID", tenant2Id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(queryReqTenant2)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.citations", hasSize(0)))
            .andExpect(jsonPath("$.executionMetrics.candidateCount", is(0)));

        // 7. Verify DELETE /api/v1/documents/{id} and cascade chunk deletion
        mockMvc.perform(delete("/api/v1/documents/" + docId)
                .header("X-Tenant-ID", tenant1Id.toString()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/documents/" + docId)
                .header("X-Tenant-ID", tenant1Id.toString()))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Security: Reject upload from non-existent or unauthorized tenant with 403 Forbidden")
    void testUploadWithNonExistentTenantRejected() throws Exception {
        UUID nonExistentTenant = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "unauthorized_doc.txt",
            "text/plain",
            "Some confidential data".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/documents/upload")
                .file(file)
                .header("X-Tenant-ID", nonExistentTenant.toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status", is(403)))
            .andExpect(jsonPath("$.message", containsString("Tenant not found or unauthorized")));
    }

    @Test
    @DisplayName("Validation: Reject upload when X-Tenant-ID header is missing with 400 Bad Request")
    void testUploadMissingTenantHeaderRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "sample.txt",
            "text/plain",
            "Valid text file content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/documents/upload")
                .file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status", is(400)))
            .andExpect(jsonPath("$.message", containsString("X-Tenant-ID")));
    }

    private byte[] createSamplePdf(String text) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(baos);
        }
        return baos.toByteArray();
    }
}
