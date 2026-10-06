# RAGLab: REST & SSE API Specification (Reconciled)

## 1. API Conventions

- **Base URI**: `/api/v1`
- **Content-Type**: `application/json` (document upload uses `multipart/form-data`, query streaming uses `text/event-stream`).
- **Authentication**: `X-API-Key: <api_key_secret>` header or `Authorization: Bearer <token>`.
- **Tenant Context**: Automatically resolved from API Key or passed via `X-Tenant-ID`.
- **Trace Context**: W3C `traceparent` header propagated across all request and response headers.

---

## 2. Document & Chunk Ingestion APIs (Spring Boot)

### 2.1 Upload Document (Asynchronous)
- **Method**: `POST /api/v1/documents/upload`
- **Request**: `multipart/form-data`
  - `file`: Binary file (`.pdf`, `.docx`, `.txt`, `.md`)
  - `chunkSize`: Integer (optional, default `512`)
  - `chunkOverlap`: Integer (optional, default `64`)
  - `metadata`: JSON string (optional)
- **Response**: `202 Accepted`
```json
{
  "documentId": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
  "versionNumber": 1,
  "status": "UPLOADED",
  "filename": "annual_report_2025.pdf",
  "fileSizeBytes": 1048576,
  "fileHashSha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "createdAt": "2026-10-10T10:30:00Z"
}
```

### 2.2 Get Ingestion Status
- **Method**: `GET /api/v1/documents/{documentId}/status`
- **Headers**: `X-Tenant-ID: <uuid>`
- **Response**: `200 OK`
```json
{
  "documentId": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
  "versionNumber": 1,
  "status": "READY",
  "stage": "INDEXED",
  "chunksCreated": 42,
  "processingDurationMs": 1420,
  "errorMessage": null,
  "updatedAt": "2026-10-10T10:30:02Z"
}
```

### 2.3 List Documents
- **Method**: `GET /api/v1/documents?page=0&size=20&status=READY`
- **Headers**: `X-Tenant-ID: <uuid>`
- **Response**: `200 OK`
```json
{
  "content": [
    {
      "id": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
      "tenantId": "8f8836db-2e9b-4395-8ee8-54ef5f29ceaa",
      "filename": "annual_report_2025.pdf",
      "contentType": "application/pdf",
      "fileSizeBytes": 1048576,
      "status": "READY",
      "metadata": {"category": "financial_report"},
      "createdAt": "2026-10-10T10:30:00Z",
      "updatedAt": "2026-10-10T10:30:02Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "page": 0,
  "size": 20
}
```

### 2.4 Patch Document Metadata
- **Method**: `PATCH /api/v1/documents/{id}/metadata`
- **Headers**: `X-Tenant-ID: <uuid>`
- **Request**: `{"department": "Finance", "year": 2026}`
- **Response**: `200 OK` (DocumentResponse)

### 2.5 Delete Document
- **Method**: `DELETE /api/v1/documents/{id}`
- **Headers**: `X-Tenant-ID: <uuid>`
- **Response**: `204 No Content`

### 2.6 Direct Chunk Creation & Vector Storage (Phase 2 Baseline)
- **Method**: `POST /api/v1/chunks`
- **Request**:
```json
{
  "documentId": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
  "tenantId": "8f8836db-2e9b-4395-8ee8-54ef5f29ceaa",
  "chunkIndex": 0,
  "content": "Quarterly net revenue grew by 24 percent...",
  "tokenCount": 10,
  "embedding": [0.012, -0.045, ... 768 floats ...],
  "metadata": {"page": 1, "section": "Highlights"}
}
```
- **Response**: `201 Created` (ChunkResponse)

---

## 3. Query & RAG Retrieval APIs (Spring Boot)

### 3.1 Standard RAG Query (Synchronous JSON)
- **Method**: `POST /api/v1/query`
- **Headers**: `X-Tenant-ID: <uuid>`
- **Request**:
```json
{
  "query": "What was the total revenue growth in fiscal year 2025?",
  "retrievalStrategy": "HYBRID_RRF",
  "topK": 5,
  "minScore": 0.65,
  "model": "llama3:8b",
  "temperature": 0.1,
  "useCache": true,
  "filters": {
    "documentIds": ["1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed"]
  }
}
```
- **Response**: `200 OK`
```json
{
  "queryId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "answer": "In fiscal year 2025, total revenue increased by 18% compared to the prior year, reaching $4.2 billion [1].",
  "citations": [
    {
      "citationIndex": 1,
      "chunkId": "e2a6b29f-5c21-4f4a-85d1-64d85203bc11",
      "documentId": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
      "documentName": "annual_report_2025.pdf",
      "pageNumber": 1,
      "snippet": "In fiscal year 2025, total revenue increased by 18%...",
      "score": 0.892
    }
  ],
  "executionMetrics": {
    "cacheHit": false,
    "retrievalLatencyMs": 18,
    "generationLatencyMs": 640,
    "totalLatencyMs": 658,
    "promptTokens": 512,
    "completionTokens": 38
  }
}
```

### 3.2 Real-Time Streaming RAG Query (Server-Sent Events - SSE)
- **Method**: `POST /api/v1/query/stream`
- **Accept**: `text/event-stream`
- **Request**: Same JSON body as `/api/v1/query`
- **SSE Stream Protocol**:
```
event: metadata
data: {"queryId":"3fa85f64-5717-4562-b3fc-2c963f66afa6","model":"llama3:8b","cached":false}

event: context
data: {"citations":[{"citationIndex":1,"documentName":"annual_report_2025.pdf","page":1}]}

event: token
data: {"delta":"In "}

event: token
data: {"delta":"fiscal "}

event: token
data: {"delta":"year 2025, total revenue increased by 18% [1]."}

event: done
data: {"totalTokens":42,"durationMs":580}
```

---

## 4. Internal Service Contract: Spring Boot $\leftrightarrow$ Python Query Service

When Spring Boot delegates retrieval to the Python Haystack service ([ADR-014](file:///d:/Project/raglab/docs/adr/ADR-014-service-boundaries-and-query-orchestration.md)):

- **Method**: `POST /internal/v1/retrieval/search`
- **Headers**: `X-Tenant-ID: <uuid>`, `X-Correlation-ID: <uuid>`, `traceparent: <w3c>`
- **Request Body**:
```json
{
  "tenantId": "8f8836db-2e9b-4395-8ee8-54ef5f29ceaa",
  "query": "What was the total revenue growth?",
  "strategy": "HYBRID_RRF",
  "topK": 5,
  "minScore": 0.50,
  "rerank": true,
  "documentIds": ["1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed"]
}
```
- **Response Body**:
```json
{
  "queryId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "candidates": [
    {
      "chunkId": "e2a6b29f-5c21-4f4a-85d1-64d85203bc11",
      "documentId": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
      "content": "In fiscal year 2025, total revenue increased by 18%...",
      "metadata": {"page": 1, "section": "Financials"},
      "denseScore": 0.892,
      "sparseScore": 4.12,
      "rrfScore": 0.032,
      "rerankScore": 0.941
    }
  ],
  "latencyMs": 22
}
```

---

## 5. Evaluation & Benchmarking APIs

### 5.1 Run Phase 4 Minimum Retrieval Evaluation
- **Method**: `POST /api/v1/evaluations/run-retrieval`
- **Request**:
```json
{
  "datasetId": "golden_docs_v1",
  "strategy": "HYBRID_RRF",
  "topK": 5,
  "compareExact": true
}
```
- **Response**: `200 OK`
```json
{
  "runId": "9c1b8972-e1cb-42b7-a37a-4c2da7135e61",
  "recallAt3": 0.91,
  "recallAt5": 0.96,
  "mrr": 0.88,
  "ndcgAt10": 0.92,
  "hnswRecallVsExact": 0.985,
  "latencyP95Ms": 14.2
}
```

### 5.2 Standard Error Format (RFC 7807)
```json
{
  "type": "https://raglab.dev/errors/retrieval-timeout",
  "title": "Retrieval Timeout",
  "status": 504,
  "detail": "Vector retrieval failed to complete within the 2000ms deadline.",
  "instance": "/api/v1/query",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "timestamp": "2026-10-10T10:30:15Z"
}
```
