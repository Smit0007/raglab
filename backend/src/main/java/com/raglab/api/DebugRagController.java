package com.raglab.api;

import com.raglab.application.dto.RetrievalQueryRequest;
import com.raglab.application.dto.VectorSearchRequest;
import com.raglab.application.dto.VectorSearchResult;
import com.raglab.application.service.RagRetrievalService;
import com.raglab.application.service.VectorSearchService;
import com.raglab.application.service.embedding.EmbeddingProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/query")
@Profile("dev") // only active in dev profile
public class DebugRagController {

    @Autowired
    private RagRetrievalService ragRetrievalService;

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private EmbeddingProvider embeddingProvider;

    /**
     * Development‑only endpoint that returns the raw vector‑search results, the generated query vector,
     * and the original query text. Useful for diagnosing similarity scores.
     */
    @PostMapping("/debug")
    public DebugResponse debug(@RequestBody RetrievalQueryRequest request) {
        // Generate real embedding (same as normal flow)
        float[] queryVector = request.queryVector();
        if (queryVector == null || queryVector.length == 0) {
            queryVector = embeddingProvider.generateEmbedding(request.query());
        }

        VectorSearchRequest searchRequest = new VectorSearchRequest(
                request.tenantId(),
                queryVector,
                request.topK(),
                request.minScore(),
                request.documentIds()
        );

        List<VectorSearchResult> results = vectorSearchService.search(searchRequest);
        return new DebugResponse(request.query(), queryVector, results);
    }

    public static record DebugResponse(String query, float[] queryVector, List<VectorSearchResult> results) {}
}
