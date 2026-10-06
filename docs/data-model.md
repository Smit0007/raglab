# RAGLab: Data Model & Storage Specification (Reconciled)

## 1. Storage Responsibility & Partitioning

RAGLab strictly partitions data across four distinct storage mediums based on durability, access patterns, and indexing requirements:

```
+---------------------------------------------------------------------------------------+
|                                  DATA PARTITIONING                                    |
+---------------------------------------------------------------------------------------+
|  1. PostgreSQL 16 + pgvector (System of Record & Vector Store)                         |
|     - Tenancy, Users, Workspaces, Documents, Document Versions, Chunks, Embeddings     |
|     - Full-text search (tsvector + GIN index), Ingestion transactional outbox         |
|     - Evaluation runs & benchmark results                                             |
+---------------------------------------------------------------------------------------+
|  2. RCache (Ephemeral In-Memory Cache - Zero Redis)                                   |
|     - Fast query response cache, Chunk/Query pre-computed embedding cache             |
|     - Session history cache, Rate limiter sliding counters                            |
+---------------------------------------------------------------------------------------+
|  3. Apache Kafka (Immutable Event Log)                                                |
|     - Document ingestion events, Chunk processing pipeline, DLQ                         |
+---------------------------------------------------------------------------------------+
|  4. Durable Blob Storage (Local Volume in Dev / S3 Compatible in Prod)                |
|     - Raw uploaded document files (.pdf, .docx, .md, .txt) with SHA-256 integrity      |
+---------------------------------------------------------------------------------------+
```

---

## 2. PostgreSQL Relational & Vector Schema

### 2.1 Applied Phase 2 Baseline Schema (Preserved)

The applied Flyway V1 schema ([V1__init_extensions_and_schema.sql](file:///d:/Project/raglab/backend/src/main/resources/db/migration/V1__init_extensions_and_schema.sql)) defines the permanent foundation:

1. **Extensions**: `uuid-ossp` and `vector`.
2. **`tenants` Table**: Multi-tenant isolation boundary with unique `api_key_hash`.
3. **`users` Table**: Tenant-scoped user accounts and RBAC roles.
4. **`documents` Table**:
   - `id`, `tenant_id`, `filename`, `content_type`, `file_size_bytes`, `file_hash_sha256`, `storage_path`, `status`, `error_message`, `metadata JSONB`, `created_at`, `updated_at`.
   - Unique constraint `uq_tenant_file_hash (tenant_id, file_hash_sha256)`.
5. **`document_versions` Table**:
   - Version history per document with `version_number`, `file_hash_sha256`, `storage_path`, `file_size_bytes`, `created_by`, `metadata JSONB`.
   - Unique constraint `uq_document_version (document_id, version_number)`.
6. **`document_chunks` Table**:
   - `id`, `document_id`, `tenant_id`, `chunk_index`, `content TEXT`, `token_count INT`.
   - `tsv_content tsvector GENERATED ALWAYS AS (to_tsvector('english', content)) STORED`.
   - `embedding vector(768)` (Baseline dense float32 vector).
   - `metadata JSONB`, `created_at`.
   - Indices:
     - `idx_chunks_tenant_doc (tenant_id, document_id)`
     - `idx_chunks_tsv USING GIN(tsv_content)`
     - `idx_chunks_embedding_hnsw USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64)`
7. **`conversations` & `messages` Tables**: Persistent chat histories with `citations JSONB` and `execution_metrics JSONB`.
8. **`experiments`, `experiment_runs`, `evaluation_results` Tables**: Research benchmark tracking tables.

---

### 2.2 Phase 6 Extension Schema: Multi-Space & Quantized Embeddings Contract

To support Matryoshka Representation Learning (MRL), half-precision `halfvec`, and binary quantization without rewriting, altering, or invalidating the Phase 2 baseline table ([ADR-010](file:///d:/Project/raglab/docs/adr/ADR-010-embedding-representation-versioning.md)), Phase 6 introduces the `chunk_embeddings` extension table alongside the transactional outbox:

#### Architectural Separation:
- **Baseline Preserved**: `document_chunks.embedding vector(768)` remains the canonical default dense float32 vector space for all Phase 2 APIs.
- **Child Representation Table**: All additional representations (Matryoshka truncations to 256/512, `halfvec` fp16, binary quantized `bit`) reside in `chunk_embeddings`.
- **Query Guard**: Queries specify target `space_id`. Comparing vectors across mismatched `space_id` values is strictly forbidden.

```sql
-- Phase 6 Migration: V2__add_chunk_embeddings_and_outbox.sql

-- 1. Embedding Spaces Registry Table (Metadata & Validation)
CREATE TABLE embedding_spaces (
    id VARCHAR(64) PRIMARY KEY, -- e.g. 'nomic-v1.5-768-fp32', 'nomic-v1.5-256-mrl', 'nomic-v1.5-768-bin'
    model_name VARCHAR(128) NOT NULL, -- e.g. 'nomic-embed-text'
    model_revision VARCHAR(64) NOT NULL, -- e.g. 'v1.5' or git commit SHA
    dimension INT NOT NULL, -- e.g. 768, 512, 256
    representation_type VARCHAR(32) NOT NULL, -- 'DENSE_FP32', 'HALFVEC_FP16', 'BINARY'
    distance_metric VARCHAR(32) NOT NULL, -- 'COSINE', 'INNER_PRODUCT', 'L2'
    normalization_policy VARCHAR(32) NOT NULL DEFAULT 'UNIT_L2', -- 'UNIT_L2' or 'NONE'
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Seed Canonical Baseline Space
INSERT INTO embedding_spaces (id, model_name, model_revision, dimension, representation_type, distance_metric, normalization_policy)
VALUES ('nomic-v1.5-768-fp32', 'nomic-embed-text', 'v1.5', 768, 'DENSE_FP32', 'COSINE', 'UNIT_L2');

-- 2. Dedicated Multi-Representation Chunk Embeddings Table
CREATE TABLE chunk_embeddings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    chunk_id UUID NOT NULL REFERENCES document_chunks(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    space_id VARCHAR(64) NOT NULL REFERENCES embedding_spaces(id) ON DELETE RESTRICT,
    dimension INT NOT NULL,
    representation_type VARCHAR(32) NOT NULL, -- DENSE_FP32, HALFVEC_FP16, BINARY
    embedding_fp32 vector,
    embedding_half halfvec,
    embedding_bin bit,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_chunk_space UNIQUE (chunk_id, space_id),
    CONSTRAINT chk_embedding_representation_presence CHECK (
        (representation_type = 'DENSE_FP32' AND embedding_fp32 IS NOT NULL) OR
        (representation_type = 'HALFVEC_FP16' AND embedding_half IS NOT NULL) OR
        (representation_type = 'BINARY' AND embedding_bin IS NOT NULL)
    )
);

-- Compound index for fast tenant and space scoped retrieval joins
CREATE INDEX idx_chunk_embeddings_lookup ON chunk_embeddings(tenant_id, space_id, chunk_id);

-- Partial HNSW Indexes per representation type
-- Dense FP32 Index (when non-null)
CREATE INDEX idx_chunk_emb_fp32_hnsw ON chunk_embeddings 
USING hnsw (embedding_fp32 vector_cosine_ops)
WHERE embedding_fp32 IS NOT NULL;

-- 3. Transactional Outbox for Ingestion Events (ADR-015)
CREATE TABLE ingestion_outbox (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_unpublished ON ingestion_outbox(published, created_at) WHERE published = FALSE;
```

---

## 3. RCache Key Schema & TTL Rules

RCache keys are namespaced, tenant-isolated, deterministic, and string/binary encoded ([ADR-004](file:///d:/Project/raglab/docs/adr/ADR-004-rcache-instead-of-redis.md), [ADR-018](file:///d:/Project/raglab/docs/adr/ADR-018-rcache-phased-roadmap.md)):

| Key Pattern | Purpose | Format / Value | Default TTL | Invalidation Trigger |
| :--- | :--- | :--- | :--- | :--- |
| `rag:query:{tenant_id}:{query_sha256}` | Cached LLM response with citations | JSON: `{response: "...", citations: [...]}` | $3600\text{s}$ (1 hour) | Evicted on tenant document publication |
| `rag:emb:{model_id}:{text_sha256}` | Cached computed dense vector | Binary float array ($768 \times 4\text{ bytes} = 3072\text{ bytes}$) | $86400\text{s}$ (24 hours) | Immutable (keyed by text & model revision) |
| `rag:session:{tenant_id}:{session_id}` | Multi-turn chat session history | JSON: `[{role: "user", text: "..."}, ...]` | $7200\text{s}$ (2 hours) | Cleared on session reset |
| `rag:ratelimit:{tenant_id}:{minute_timestamp}` | API rate limiting sliding counter | Integer | $60\text{s}$ | Natural TTL expiry |

---

## 4. Kafka Event Schemas

Kafka messages use versioned JSON serialization with tenant routing keys:

### 4.1 Topic: `raglab.document.ingestion.v1`
Key: `tenantId:documentId`
```json
{
  "eventId": "c8f5f488-82db-424a-b501-c529cfde3a12",
  "eventType": "DOCUMENT_UPLOADED",
  "timestamp": "2026-10-10T10:30:00Z",
  "tenantId": "8f8836db-2e9b-4395-8ee8-54ef5f29ceaa",
  "documentId": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
  "versionNumber": 1,
  "storageUri": "blob://tenant-1/1b9d6bcd/v1.pdf",
  "contentType": "application/pdf",
  "filename": "annual_report_2025.pdf",
  "fileSizeBytes": 1048576,
  "fileHashSha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "chunkingStrategy": {
    "type": "RECURSIVE_CHARACTER",
    "chunkSize": 512,
    "chunkOverlap": 64
  }
}
```

### 4.2 Topic: `raglab.document.chunks.v1`
Key: `documentId`
```json
{
  "eventId": "e9b253b4-53bf-4786-8d6a-54314c44243b",
  "eventType": "CHUNKS_GENERATED",
  "timestamp": "2026-10-10T10:30:05Z",
  "tenantId": "8f8836db-2e9b-4395-8ee8-54ef5f29ceaa",
  "documentId": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
  "versionNumber": 1,
  "chunkId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "chunkIndex": 0,
  "totalChunks": 24,
  "content": "In fiscal year 2025, total revenue increased by 18%...",
  "tokenCount": 84,
  "metadata": {
    "page": 1,
    "header": "Financial Highlights"
  }
}
```

### 4.3 Topic: `raglab.ingestion.dlq.v1`
Key: `documentId`
```json
{
  "eventId": "a7b311fc-64bb-4c28-98e3-057a66144eef",
  "failedEventPayload": { ... },
  "failureReason": "OLLAMA_CONNECTION_TIMEOUT",
  "stackTrace": "java.net.SocketTimeoutException: Connect timed out...",
  "retryCount": 3,
  "failedAt": "2026-10-10T10:31:00Z"
}
```
