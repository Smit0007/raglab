package com.raglab.api;

import com.raglab.application.dto.RetrievalQueryRequest;
import com.raglab.application.dto.RetrievalQueryResponse;
import com.raglab.application.service.RagRetrievalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/query")
public class QueryController {

    private final RagRetrievalService ragRetrievalService;

    public QueryController(RagRetrievalService ragRetrievalService) {
        this.ragRetrievalService = ragRetrievalService;
    }

    @PostMapping
    public ResponseEntity<RetrievalQueryResponse> executeQuery(
        @RequestHeader(value = "X-Tenant-ID", required = false) UUID headerTenantId,
        @RequestBody RetrievalQueryRequest request
    ) {
        UUID effectiveTenantId = request.tenantId() != null ? request.tenantId() : headerTenantId;
        if (effectiveTenantId == null) {
            throw new IllegalArgumentException("tenantId is required (either in body or X-Tenant-ID header)");
        }

        RetrievalQueryRequest resolvedRequest = new RetrievalQueryRequest(
            effectiveTenantId,
            request.query(),
            request.queryVector(),
            request.retrievalStrategy(),
            request.topK(),
            request.minScore(),
            request.documentIds(),
            request.model(),
            request.temperature(),
            request.useCache()
        );

        RetrievalQueryResponse response = ragRetrievalService.executeQuery(resolvedRequest);
        return ResponseEntity.ok(response);
    }
}
