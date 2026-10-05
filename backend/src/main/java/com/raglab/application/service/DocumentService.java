package com.raglab.application.service;

import com.raglab.application.dto.CreateDocumentRequest;
import com.raglab.application.dto.DocumentResponse;
import com.raglab.domain.entity.DocumentEntity;
import com.raglab.domain.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public DocumentResponse createDocument(CreateDocumentRequest request) {
        if (request.tenantId() == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (request.filename() == null || request.filename().isBlank()) {
            throw new IllegalArgumentException("filename is required");
        }

        DocumentEntity entity = new DocumentEntity(
            request.tenantId(),
            request.filename(),
            request.contentType() != null ? request.contentType() : "application/octet-stream",
            request.fileSizeBytes() != null ? request.fileSizeBytes() : 0L,
            request.fileHashSha256() != null ? request.fileHashSha256() : UUID.randomUUID().toString().replace("-", ""),
            request.storagePath() != null ? request.storagePath() : "/data/uploads/" + request.filename(),
            request.metadata()
        );

        DocumentEntity saved = documentRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Optional<DocumentResponse> getDocument(UUID id, UUID tenantId) {
        return documentRepository.findByIdAndTenantId(id, tenantId).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments(UUID tenantId) {
        return documentRepository.findAllByTenantId(tenantId).stream()
            .map(this::toResponse)
            .toList();
    }

    public DocumentResponse updateDocumentStatus(UUID id, UUID tenantId, String status, String errorMessage) {
        DocumentEntity doc = documentRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + id));
        doc.setStatus(status);
        doc.setErrorMessage(errorMessage);
        return toResponse(documentRepository.save(doc));
    }

    public DocumentResponse updateDocumentMetadata(UUID id, UUID tenantId, Map<String, Object> metadata) {
        DocumentEntity doc = documentRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + id));
        doc.setMetadata(metadata);
        return toResponse(documentRepository.save(doc));
    }

    public void deleteDocument(UUID id, UUID tenantId) {
        DocumentEntity doc = documentRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + id));
        documentRepository.delete(doc);
    }

    private DocumentResponse toResponse(DocumentEntity entity) {
        return new DocumentResponse(
            entity.getId(),
            entity.getTenantId(),
            entity.getFilename(),
            entity.getContentType(),
            entity.getFileSizeBytes(),
            entity.getFileHashSha256(),
            entity.getStoragePath(),
            entity.getStatus(),
            entity.getErrorMessage(),
            entity.getMetadata(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
