package com.raglab.api;

import com.raglab.application.dto.RetrievalEvaluationRequest;
import com.raglab.application.dto.RetrievalEvaluationResponse;
import com.raglab.application.service.RetrievalEvaluationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/evaluations")
public class EvaluationController {

    private final RetrievalEvaluationService retrievalEvaluationService;

    public EvaluationController(RetrievalEvaluationService retrievalEvaluationService) {
        this.retrievalEvaluationService = retrievalEvaluationService;
    }

    @PostMapping("/run-retrieval")
    public ResponseEntity<RetrievalEvaluationResponse> runRetrievalEvaluation(
        @RequestHeader(value = "X-Tenant-ID", required = false) UUID headerTenantId,
        @RequestBody(required = false) RetrievalEvaluationRequest request
    ) {
        UUID tenantId = (headerTenantId != null) ? headerTenantId : UUID.fromString("00000000-0000-0000-0000-000000000001");
        RetrievalEvaluationRequest resolvedRequest = (request != null)
            ? request
            : new RetrievalEvaluationRequest("golden_docs_v1", "HYBRID_RRF", 5, true);

        RetrievalEvaluationResponse response = retrievalEvaluationService.runRetrievalEvaluation(tenantId, resolvedRequest);
        return ResponseEntity.ok(response);
    }
}
