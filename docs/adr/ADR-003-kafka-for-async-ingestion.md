# ADR-003: Apache Kafka for Asynchronous Document Ingestion Pipeline

## Status
Accepted

## Context
Document ingestion in a RAG system involves computationally heavy, multi-stage pipelines:
1. File format parsing (PDF text extraction, OCR, docx structure).
2. Semantic chunking and token counting.
3. Neural embedding generation (batch calls to embedding models).
4. Relational and vector index persistence.

Executing this synchronously inside the HTTP request/response thread causes client request timeouts, blocks server worker threads, and leaves the system vulnerable to cascading failures when embedding models slow down or restart.

## Decision
We decide to adopt **Apache Kafka** as the asynchronous event backbone for document ingestion and chunk processing in RAGLab.

- HTTP endpoints (`POST /api/v1/documents/upload`) persist the raw file, generate a `PENDING` database record, publish a `DocumentIngestionEvent` to Kafka, and immediately return `202 Accepted` with a `jobId`.
- Scalable consumer worker pools consume ingestion events, perform chunking and embedding in batches, and write to PostgreSQL with backpressure control.
- Dead-Letter Queues (DLQs) isolate poisoned or failed documents for manual inspection or automated retry without stalling the pipeline.

## Alternatives Considered
1. **Synchronous Ingestion via HTTP Threads**:
   - *Pros*: Simple, no external message broker required.
   - *Cons*: HTTP timeouts, thread exhaustion under concurrent uploads, zero backpressure management.
2. **Spring `@Async` / ThreadPoolTaskExecutor**:
   - *Pros*: No external broker infrastructure.
   - *Cons*: In-memory queue lost on application crash or restart, no horizontal worker scaling across nodes, difficult retry/backoff coordination.
3. **RabbitMQ / SQS**:
   - *Pros*: Simple queue management.
   - *Cons*: Lacks Kafka's high-throughput partitioned event replayability, distributed log semantics, and ecosystem synergy with stream processing.

## Consequences
- **Positive**: Immediate HTTP response times, reliable backpressure, distributed horizontal worker scaling, zero lost ingestion jobs on server crashes.
- **Negative**: Adds Kafka (KRaft mode) to the operational infrastructure footprint.

## Future Reconsideration
For lightweight, local single-node development modes where running Kafka is undesirable, a pluggable in-memory / virtual thread event dispatcher could be provided as an alternative profile.
