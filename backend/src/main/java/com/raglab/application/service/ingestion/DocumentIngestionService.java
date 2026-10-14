package com.raglab.application.service.ingestion;

import com.pgvector.PGvector;
import com.raglab.application.dto.DocumentUploadResponse;
import com.raglab.application.service.chunking.ChunkSpec;
import com.raglab.application.service.chunking.DocumentChunker;
import com.raglab.application.service.embedding.EmbeddingProvider;
import com.raglab.application.service.extraction.DocumentExtractor;
import com.raglab.application.service.extraction.ExtractedDocument;
import com.raglab.domain.entity.DocumentChunkEntity;
import com.raglab.domain.entity.DocumentEntity;
import com.raglab.domain.repository.DocumentChunkRepository;
import com.raglab.domain.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;

@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final List<DocumentExtractor> extractors;
    private final DocumentChunker chunker;
    private final EmbeddingProvider embeddingProvider;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final com.raglab.domain.repository.TenantRepository tenantRepository;
    private final com.raglab.application.service.embedding.MultiSpaceEmbeddingService multiSpaceEmbeddingService;
    private final com.raglab.domain.repository.IngestionOutboxRepository outboxRepository;
    private final long maxFileSize;

    public DocumentIngestionService(
        List<DocumentExtractor> extractors,
        DocumentChunker chunker,
        EmbeddingProvider embeddingProvider,
        DocumentRepository documentRepository,
        DocumentChunkRepository chunkRepository,
        com.raglab.domain.repository.TenantRepository tenantRepository,
        com.raglab.application.service.embedding.MultiSpaceEmbeddingService multiSpaceEmbeddingService,
        com.raglab.domain.repository.IngestionOutboxRepository outboxRepository,
        @Value("${raglab.ingestion.max-file-size:20971520}") long maxFileSize // 20 MB
    ) {
        this.extractors = extractors;
        this.chunker = chunker;
        this.embeddingProvider = embeddingProvider;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.tenantRepository = tenantRepository;
        this.multiSpaceEmbeddingService = multiSpaceEmbeddingService;
        this.outboxRepository = outboxRepository;
        this.maxFileSize = maxFileSize;
    }

    @Transactional
    public DocumentUploadResponse ingestDocument(UUID tenantId, MultipartFile file) {
        if (tenantId == null) {
            throw new IllegalArgumentException("X-Tenant-ID header is required");
        }
        if (!tenantRepository.existsById(tenantId)) {
            throw new SecurityException("Tenant not found or unauthorized: " + tenantId);
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty");
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("File size exceeds maximum permitted limit (" + (maxFileSize / (1024 * 1024)) + " MB)");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = "document_" + UUID.randomUUID();
        }

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        long startTime = System.currentTimeMillis();

        // 1. Validation & Hash Calculation
        String fileHashSha256;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(file.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            fileHashSha256 = sb.toString();
        } catch (Exception e) {
            fileHashSha256 = UUID.randomUUID().toString().replace("-", "");
        }

        final String finalFileHash = fileHashSha256;
        Optional<DocumentEntity> existing = documentRepository.findAllByTenantId(tenantId).stream()
            .filter(d -> finalFileHash.equals(d.getFileHashSha256()))
            .findFirst();

        DocumentEntity document;
        if (existing.isPresent()) {
            document = existing.get();
            // Clean up old chunks for re-indexing
            chunkRepository.deleteAllByDocumentId(document.getId());
        } else {
            document = new DocumentEntity(
                tenantId,
                filename,
                contentType,
                file.getSize(),
                fileHashSha256,
                "/data/uploads/" + tenantId + "/" + filename,
                new HashMap<>()
            );
        }

        document.setStatus("EXTRACTING");
        document = documentRepository.save(document);

        // 2. Text Extraction
        ExtractedDocument extractedDoc = null;
        DocumentExtractor matchedExtractor = extractors.stream()
            .filter(ext -> ext.supports(file.getContentType(), file.getOriginalFilename()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported document format: " + file.getOriginalFilename()));

        try (InputStream is = file.getInputStream()) {
            extractedDoc = matchedExtractor.extract(is, filename, contentType);
        } catch (Exception e) {
            document.setStatus("FAILED");
            document.setErrorMessage("Extraction failed: " + e.getMessage());
            documentRepository.save(document);
            throw new RuntimeException("Text extraction failed: " + e.getMessage(), e);
        }

        // Update metadata with extraction stats
        Map<String, Object> meta = document.getMetadata() != null ? document.getMetadata() : new HashMap<>();
        meta.put("pageCount", extractedDoc.pageCount());
        meta.putAll(extractedDoc.metadata());
        document.setMetadata(meta);

        // 3. Text Chunking
        document.setStatus("CHUNKING");
        documentRepository.save(document);

        List<ChunkSpec> chunkSpecs = chunker.chunkDocument(extractedDoc);
        if (chunkSpecs.isEmpty()) {
            document.setStatus("FAILED");
            document.setErrorMessage("Document produced zero extractable chunks");
            documentRepository.save(document);
            throw new IllegalStateException("Document produced zero extractable chunks");
        }

        // 4. Embedding Generation & Indexing
        document.setStatus("EMBEDDING");
        documentRepository.save(document);

        int embeddedCount = 0;
        List<DocumentChunkEntity> chunkEntities = new ArrayList<>();

        for (ChunkSpec spec : chunkSpecs) {
            float[] vector = embeddingProvider.generateEmbedding(spec.content());
            if (vector == null || vector.length != embeddingProvider.getDimension()) {
                document.setStatus("FAILED");
                document.setErrorMessage("Embedding generation returned invalid vector dimension");
                documentRepository.save(document);
                throw new IllegalStateException("Embedding dimension mismatch: expected " + embeddingProvider.getDimension());
            }

            String vectorString = new PGvector(vector).getValue();

            DocumentChunkEntity chunkEntity = new DocumentChunkEntity(
                document.getId(),
                tenantId,
                spec.chunkIndex(),
                spec.content(),
                spec.tokenCount(),
                vectorString,
                spec.metadata()
            );

            chunkEntities.add(chunkEntity);
            embeddedCount++;
        }

        // 5. PostgreSQL Persistence
        document.setStatus("INDEXING");
        documentRepository.save(document);

        List<DocumentChunkEntity> savedChunks = chunkRepository.saveAll(chunkEntities);

        // Generate and persist multi-space representations (Phase 6)
        for (int i = 0; i < savedChunks.size(); i++) {
            DocumentChunkEntity savedChunk = savedChunks.get(i);
            ChunkSpec spec = chunkSpecs.get(i);
            float[] vec = embeddingProvider.generateEmbedding(spec.content());
            multiSpaceEmbeddingService.generateAndPersistRepresentations(savedChunk.getId(), tenantId, vec);
        }

        // 6. Record Transactional Outbox Event (ADR-015 / Phase 10)
        Map<String, Object> outboxPayload = new HashMap<>();
        outboxPayload.put("documentId", document.getId());
        outboxPayload.put("tenantId", tenantId);
        outboxPayload.put("filename", document.getFilename());
        outboxPayload.put("chunkCount", savedChunks.size());
        outboxPayload.put("timestamp", System.currentTimeMillis());
        outboxRepository.save(new com.raglab.domain.entity.IngestionOutboxEntity(tenantId, "DOCUMENT_INGESTED", outboxPayload));

        document.setStatus("COMPLETED");
        document.setErrorMessage(null);
        document = documentRepository.save(document);

        long processingDuration = System.currentTimeMillis() - startTime;
        log.info("Successfully ingested document '{}' (id={}) with {} chunks in {} ms",
            filename, document.getId(), chunkEntities.size(), processingDuration);

        return new DocumentUploadResponse(
            document.getId(),
            document.getFilename(),
            document.getFileSizeBytes(),
            document.getStatus(),
            extractedDoc.pageCount(),
            chunkEntities.size(),
            embeddedCount,
            processingDuration
        );
    }
}
