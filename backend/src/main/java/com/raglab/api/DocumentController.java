package com.raglab.api;

import com.raglab.application.dto.CreateDocumentRequest;
import com.raglab.application.dto.DocumentDetailResponse;
import com.raglab.application.dto.DocumentResponse;
import com.raglab.application.dto.DocumentUploadResponse;
import com.raglab.application.service.DocumentChunkService;
import com.raglab.application.service.DocumentService;
import com.raglab.application.service.ingestion.DocumentIngestionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentIngestionService ingestionService;
    private final DocumentChunkService chunkService;

    public DocumentController(
        DocumentService documentService,
        DocumentIngestionService ingestionService,
        DocumentChunkService chunkService
    ) {
        this.documentService = documentService;
        this.ingestionService = ingestionService;
        this.chunkService = chunkService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
        @RequestHeader("X-Tenant-ID") UUID tenantId,
        @RequestParam("file") MultipartFile file
    ) {
        DocumentUploadResponse response = ingestionService.ingestDocument(tenantId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> createDocument(@RequestBody CreateDocumentRequest request) {
        DocumentResponse response = documentService.createDocument(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentDetailResponse> getDocument(
        @PathVariable("id") UUID id,
        @RequestHeader("X-Tenant-ID") UUID tenantId
    ) {
        return documentService.getDocument(id, tenantId)
            .map(doc -> {
                int totalChunks = chunkService.getChunksByDocumentId(id).size();
                Integer pageCount = null;
                if (doc.metadata() != null && doc.metadata().get("pageCount") instanceof Number n) {
                    pageCount = n.intValue();
                }
                return ResponseEntity.ok(new DocumentDetailResponse(
                    doc.id(),
                    doc.tenantId(),
                    doc.filename(),
                    doc.contentType(),
                    doc.fileSizeBytes(),
                    doc.status(),
                    doc.errorMessage(),
                    doc.metadata(),
                    pageCount,
                    totalChunks,
                    totalChunks,
                    doc.createdAt(),
                    doc.updatedAt()
                ));
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<DocumentDetailResponse>> listDocuments(
        @RequestHeader("X-Tenant-ID") UUID tenantId
    ) {
        List<DocumentResponse> documents = documentService.listDocuments(tenantId);
        List<DocumentDetailResponse> details = documents.stream().map(doc -> {
            int totalChunks = chunkService.getChunksByDocumentId(doc.id()).size();
            Integer pageCount = null;
            if (doc.metadata() != null && doc.metadata().get("pageCount") instanceof Number n) {
                pageCount = n.intValue();
            }
            return new DocumentDetailResponse(
                doc.id(),
                doc.tenantId(),
                doc.filename(),
                doc.contentType(),
                doc.fileSizeBytes(),
                doc.status(),
                doc.errorMessage(),
                doc.metadata(),
                pageCount,
                totalChunks,
                totalChunks,
                doc.createdAt(),
                doc.updatedAt()
            );
        }).toList();

        return ResponseEntity.ok(details);
    }

    @PatchMapping("/{id}/metadata")
    public ResponseEntity<DocumentResponse> updateMetadata(
        @PathVariable("id") UUID id,
        @RequestHeader("X-Tenant-ID") UUID tenantId,
        @RequestBody Map<String, Object> metadata
    ) {
        DocumentResponse response = documentService.updateDocumentMetadata(id, tenantId, metadata);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
        @PathVariable("id") UUID id,
        @RequestHeader("X-Tenant-ID") UUID tenantId
    ) {
        documentService.deleteDocument(id, tenantId);
        return ResponseEntity.noContent().build();
    }
}
