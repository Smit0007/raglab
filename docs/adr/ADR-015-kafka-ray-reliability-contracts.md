# ADR-015: Kafka and Ray Ingestion Reliability and Dual-Write Contracts

## Status
Accepted

## Context
Asynchronous document ingestion bridges Spring Boot, Apache Kafka, Python Ray worker nodes, and PostgreSQL. In such distributed ingestion pipelines, network partitions, worker crashes, and duplicate delivery can result in state inconsistency (e.g., database record exists as `PENDING` but Kafka message was lost, or Ray task re-executes and inserts duplicate chunks).

## Decision
We define an end-to-end **Reliability and Dual-Write Contract** across PostgreSQL, Kafka, and Ray:

### 1. Dual-Write Prevention: Transactional Outbox Pattern
- To solve the dual-write problem between PostgreSQL (saving `DocumentEntity` / `DocumentVersionEntity`) and Kafka (publishing `DocumentIngestionEvent`):
- Spring Boot uses a **Transactional Outbox Table**:
  ```sql
  CREATE TABLE ingestion_outbox (
      id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
      tenant_id UUID NOT NULL,
      event_type VARCHAR(64) NOT NULL,
      payload JSONB NOT NULL,
      created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
      published BOOLEAN DEFAULT FALSE
  );
  ```
- Document upload writes the initial `DocumentEntity` and the outbox record in a single local ACID database transaction.
- An asynchronous relay publisher (Spring `@Scheduled` polling or Debezium CDC) emits the event to Kafka and marks it `published = true`. This guarantees **at-least-once delivery** with zero risk of database/Kafka skew.

### 2. At-Least-Once Delivery and Consumer Acknowledgment
- Kafka consumer groups (Python Ray dispatchers) use **manual offset commit** (`enable.auto.commit = false`).
- Offsets are committed only after the batch of chunks has been durably acknowledged by downstream storage or Ray checkpoint.

### 3. Ray Worker Crash Recovery and Task Idempotency
- Ray tasks for parsing and chunking are configured with `max_retries = 3` and `retry_exceptions = True`.
- **Chunk Identity Invariant**: All generated chunks use deterministic IDs derived from the version ID, chunk index, and SHA-256 content hash.
- Storage writes execute idempotent upserts (`INSERT ... ON CONFLICT (version_id, chunk_index) DO UPDATE ...`). Repeated Ray task execution produces identical database state without generating duplicate chunks.

### 4. Dead-Letter Queue (DLQ) and Terminal Failures
- If a document fails processing after 3 exponential backoff retries (e.g. malformed corrupt PDF, unsupported encryption):
  1. The event is published to `raglab.ingestion.dlq.v1` with error stack trace and metadata.
  2. The database record is transitioned to `FAILED` with detailed `error_message`.
  3. An alert metric `raglab_ingestion_failures_total` is incremented.

## Consequences
- **Positive**: Strict data integrity; immunity to Kafka producer dual-write loss; safe worker restarts and redeliveries without data duplication.
- **Negative**: Introduces outbox table polling overhead and requires dead-letter monitoring.
