package com.raglab.api;

import com.raglab.application.dto.VectorSearchRequest;
import com.raglab.application.dto.VectorSearchResult;
import com.raglab.application.service.VectorSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
public class VectorSearchController {

    private final VectorSearchService vectorSearchService;

    public VectorSearchController(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }

    @PostMapping("/vector")
    public ResponseEntity<List<VectorSearchResult>> searchVectors(@RequestBody VectorSearchRequest request) {
        List<VectorSearchResult> results = vectorSearchService.search(request);
        return ResponseEntity.ok(results);
    }
}
