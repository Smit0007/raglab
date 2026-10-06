# ADR-011: Document and Ingestion Lifecycle State Machine

## Status
Accepted

## Context
RAGLab documents undergo complex asynchronous ingestion: upload, source verification, parsing, text chunking, embedding generation, vector indexing, and publication.
Without an explicit, deterministic lifecycle state machine:
1. Incomplete or partially embedded documents risk being exposed to user queries, producing degraded answers.
2. Replacing an existing document version could take the existing knowledge offline during re-ingestion.
3. Network blips, worker crashes, or Kafka redelivery could produce duplicate chunks, orphaned records, or stuck jobs.

## Decision
We establish a formalized **Document and Ingestion Lifecycle State Machine** with atomic publication boundaries and idempotent version isolation:

### 1. Document Lifecycle States (`documents.status` and `document_versions.status`)
1. `UPLOADED`: Source binary received, SHA-256 validated, stored in durable source storage, metadata recorded.
2. `QUEUED`: Ingestion job dispatched to Kafka `raglab.document.ingestion.v1`.
3. `PARSING`: Worker active, extracting text and structural sections (Ray task).
4. `CHUNKING`: Generating deterministic chunks with stable composite keys.
5. `EMBEDDING`: Batch-generating dense vectors via model runtime.
6. `INDEXING`: Writing chunk records and updating vector/lexical indexes in PostgreSQL.
7. `READY`: Fully indexed, verified against invariants, and published as active/queryable.
8. `FAILED`: Terminal failure encountered (or max retries exhausted). Error message recorded.
9. `RETIRED`: Replaced by a newer published version; archived or scheduled for deletion.

### 2. Version Publication Invariant (Blue/Green Versioning)
- When a document is updated, a new `DocumentVersion` ($V_{n+1}$) is created in `UPLOADED` state.
- **Active Query Invariant**: All retrieval queries filter by published, active versions (`status = 'READY'`). The existing version $V_n$ remains 100% active and queryable while $V_{n+1}$ is being processed.
- **Atomic Cutover**: Only when $V_{n+1}$ reaches `INDEXING` completion and passes chunk-count validation does a single database transaction set $V_{n+1} \rightarrow \text{READY}$, $V_n \rightarrow \text{RETIRED}$, and update `documents.status = 'READY'`.

### 3. Idempotency and Recovery Contracts
- **Stable Chunk Identity**: `chunk_id` is derived deterministically from `UUID_v5(namespace, tenant_id + ":" + document_version_id + ":" + chunk_index + ":" + content_sha256)`.
- **Database Upsert Guarantees**: A unique constraint `uq_version_chunk_index (version_id, chunk_index)` prevents duplicate chunks upon Kafka redelivery.
- **Worker Recovery**: If a worker crashes mid-ingestion, Kafka message redelivery invokes idempotent chunk writes (`ON CONFLICT (version_id, chunk_index) DO UPDATE...`) or atomic version-stage replay.

## Consequences
- **Positive**: Zero query downtime during document updates; elimination of "ghost" chunks from interrupted ingestion jobs; strict audit trail per version.
- **Negative**: Temporary dual-storage consumption while both $V_n$ and $V_{n+1}$ exist during processing.
