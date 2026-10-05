package com.raglab.api;

import com.raglab.application.dto.CreateDocumentRequest;
import com.raglab.application.dto.DocumentResponse;
import com.raglab.application.service.DocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> createDocument(@RequestBody CreateDocumentRequest request) {
        DocumentResponse response = documentService.createDocument(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(
        @PathVariable("id") UUID id,
        @RequestHeader("X-Tenant-ID") UUID tenantId
    ) {
        return documentService.getDocument(id, tenantId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> listDocuments(
        @RequestHeader("X-Tenant-ID") UUID tenantId
    ) {
        List<DocumentResponse> documents = documentService.listDocuments(tenantId);
        return ResponseEntity.ok(documents);
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
