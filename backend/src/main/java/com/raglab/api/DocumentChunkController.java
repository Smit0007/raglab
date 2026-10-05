package com.raglab.api;

import com.raglab.application.dto.ChunkResponse;
import com.raglab.application.dto.CreateChunkRequest;
import com.raglab.application.service.DocumentChunkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chunks")
public class DocumentChunkController {

    private final DocumentChunkService chunkService;

    public DocumentChunkController(DocumentChunkService chunkService) {
        this.chunkService = chunkService;
    }

    @PostMapping
    public ResponseEntity<ChunkResponse> createChunk(@RequestBody CreateChunkRequest request) {
        ChunkResponse response = chunkService.createChunk(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChunkResponse> getChunk(@PathVariable("id") UUID id) {
        return chunkService.getChunk(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/document/{documentId}")
    public ResponseEntity<List<ChunkResponse>> getChunksByDocumentId(@PathVariable("documentId") UUID documentId) {
        List<ChunkResponse> chunks = chunkService.getChunksByDocumentId(documentId);
        return ResponseEntity.ok(chunks);
    }

    @PutMapping("/{id}/embedding")
    public ResponseEntity<ChunkResponse> updateEmbedding(
        @PathVariable("id") UUID id,
        @RequestBody float[] embedding
    ) {
        ChunkResponse response = chunkService.updateEmbedding(id, embedding);
        return ResponseEntity.ok(response);
    }
}
