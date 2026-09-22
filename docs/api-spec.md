# RAGLab: REST & SSE API Specification

## 1. API Conventions

- **Base URI**: `/api/v1`
- **Content-Type**: `application/json` (except document upload which uses `multipart/form-data` and streaming which uses `text/event-stream`).
- **Authentication**: `X-API-Key: <api_key_secret>` header or `Authorization: Bearer <token>`.
- **Tenant Context**: Automatically resolved from API Key or passed via `X-Tenant-ID`.
- **Trace Context**: W3C `traceparent` header propagated in request and response headers.

---

## 2. Document Ingestion APIs

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
  "jobId": "f784e201-9a73-4217-a065-24cfc37e6931",
  "status": "QUEUED",
  "filename": "annual_report_2025.pdf",
  "fileSizeBytes": 1048576,
  "createdAt": "2026-10-04T10:30:00Z"
}
```

### 2.2 Get Ingestion Job Status
- **Method**: `GET /api/v1/documents/{documentId}/status`
- **Response**: `200 OK`
```json
{
  "documentId": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
  "jobId": "f784e201-9a73-4217-a065-24cfc37e6931",
  "status": "INDEXED",
  "stage": "COMPLETED",
  "chunksCreated": 42,
  "processingDurationMs": 1420,
  "error": null,
  "updatedAt": "2026-10-04T10:30:02Z"
}
```

### 2.3 List Documents
- **Method**: `GET /api/v1/documents?page=0&size=20&status=INDEXED`
- **Response**: `200 OK`
```json
{
  "content": [
    {
      "id": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
      "filename": "annual_report_2025.pdf",
      "contentType": "application/pdf",
      "fileSizeBytes": 1048576,
      "status": "INDEXED",
      "chunkCount": 42,
      "createdAt": "2026-10-04T10:30:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "page": 0,
  "size": 20
}
```

### 2.4 Delete Document
- **Method**: `DELETE /api/v1/documents/{documentId}`
- **Response**: `204 No Content`

---

## 3. Query & RAG Retrieval APIs

### 3.1 Standard RAG Query (Synchronous JSON)
- **Method**: `POST /api/v1/query`
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
data: {"delta":"year "}

event: token
data: {"delta":"2025, total revenue increased by 18% [1]."}

event: done
data: {"totalTokens":42,"durationMs":580}
```

---

## 4. Evaluation & Benchmarking APIs

### 4.1 Run Single Evaluation Test
- **Method**: `POST /api/v1/evaluations/run-single`
- **Request**:
```json
{
  "query": "What was the total revenue growth in 2025?",
  "groundTruth": "Revenue grew by 18% to $4.2B in 2025.",
  "retrievalStrategy": "HYBRID_RRF",
  "llmModel": "llama3:8b"
}
```
- **Response**: `200 OK`
```json
{
  "contextRelevance": 0.95,
  "groundedness": 1.0,
  "answerRelevance": 0.92,
  "ragTriadPass": true,
  "latencyMs": 480
}
```

### 4.2 Trigger Batch Benchmark Run
- **Method**: `POST /api/v1/evaluations/benchmark`
- **Request**:
```json
{
  "name": "llama3_vs_mistral_hybrid_benchmark",
  "dataset": "finance_qa_v1",
  "strategies": ["VECTOR", "BM25", "HYBRID_RRF"],
  "models": ["llama3:8b", "mistral:7b"]
}
```
- **Response**: `202 Accepted`
```json
{
  "runId": "9c1b8972-e1cb-42b7-a37a-4c2da7135e61",
  "status": "QUEUED",
  "totalTestCases": 150
}
```

---

## 5. Standard Error Representation

All error responses adhere to RFC 7807 (`application/problem+json`):
```json
{
  "type": "https://raglab.dev/errors/retrieval-timeout",
  "title": "Retrieval Timeout",
  "status": 504,
  "detail": "Vector retrieval failed to complete within the 5000ms deadline.",
  "instance": "/api/v1/query",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "timestamp": "2026-10-04T10:30:15Z"
}
```
