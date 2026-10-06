# RAGLab: System Architecture Specification (Reconciled)

## 1. Architectural Philosophy & Strategy

RAGLab is engineered under five core architectural pillars:
1. **Modular Monolith First**: The core application domain is encapsulated into high-cohesion, low-coupling modules inside a single Java 21 Spring Boot application codebase (`api`, `application`, `domain`, `infrastructure`, `config`). This eliminates distributed transaction complexity, network serialization penalties, and deployment friction while maintaining clean boundaries.
2. **Independent Datastore Engineering (RCache - Zero Redis)**: RCache is an independent, custom-engineered in-memory datastore with its own proprietary frame protocol (RCache Protocol - **RCP**) and custom Java driver (`rcache-client`). **Strictly zero Redis libraries, commands, or protocol dependencies are permitted.**
3. **Fail-Open Caching & Graceful Degradation**: The cache is purely an acceleration layer. Any failure in RCache (timeout, crash, network partition) immediately trips circuit breakers to bypass RCache and route directly to PostgreSQL and Ollama without user-facing failures.
4. **Decoupled Python Micro-Engines for Specialized Workloads**:
   - **Distributed Ingestion (Phase 5)**: Python + Ray cluster workers handle heterogeneous file extraction, parsing, OCR, and deterministic chunking.
   - **Query Orchestration Pipeline**: Python Query Service executes production multi-branch retrieval, Reciprocal Rank Fusion, and cross-encoder neural reranking using **Haystack** (with **LlamaIndex** evaluated as an alternative in Phase 4 research benchmarks).
   - **Generation Gateway**: Spring Boot maintains public API contracts, SSE token streaming, prompt assembly, and LangChain4j model gateways, utilizing Python as a structured evidence retrieval engine ([ADR-014](file:///d:/Project/raglab/docs/adr/ADR-014-service-boundaries-and-query-orchestration.md)).
5. **Asynchronous Ingestion Reliability & Dual-Write Prevention**: Heavy ingestion jobs are offloaded to Kafka event streams with transactional outbox protection in PostgreSQL ([ADR-015](file:///d:/Project/raglab/docs/adr/ADR-015-kafka-ray-reliability-contracts.md)).

---

## 2. High-Level System Architecture

```
                                      +------------------------------------+
                                      |         Next.js 14 Frontend        |
                                      |   (React / TypeScript / Tailwind)  |
                                      +-----------------+------------------+
                                                        |
                                           HTTPS / REST | SSE (Streaming)
                                                        v
+---------------------------------------------------------------------------------------------------------------+
|                                      RAGLab Backend (Spring Boot 3.3 / Java 21)                                |
|                                                                                                               |
|  +-----------------------+     +-----------------------+     +---------------------+     +-----------------+  |
|  |   Ingestion Gateway   |     |  Generation Gateway   |     |  Document Lifecycle |     | Evaluation Mod  |  |
|  | - Upload Validation  |     | - Prompt Assembly     |     | - Version Cutover   |     | - Research Runs |  |
|  | - Transactional Outbox|     | - LangChain4j Model   |     | - Tenant Context    |     | - Metric Logs   |  |
|  | - Blob Storage Client |     | - Citation Resolver   |     | - Audit Trail       |     | - Benchmark Rep |  |
|  | - Kafka Event Pub     |     | - SSE Token Streamer  |     | - Status Tracker    |     |                 |  |
|  +-----------+-----------+     +-----------+-----------+     +----------+----------+     +--------+--------+  |
|              |                             |                            |                         |           |
|              |                             +-------------+--------------+                         |           |
|              v                                           v                                        v           |
|  +-----------------------+                 +---------------------------+             +---------------------+  |
|  |  PostgreSQL Outbox    |                 |      RCache Client        |             |  Telemetry / Traces |  |
|  |  Async Event Relay    |                 | - Custom Binary Protocol  |             |  OpenTelemetry SDK  |  |
|  |  (At-Least-Once Pub)  |                 | - Circuit Breaker / Pool  |             +----------+----------+  |
|  +-----------+-----------+                 +-------------+-------------+                        |             |
+--------------|-------------------------------------------|--------------------------------------|-------------+
               |                                           |                                      |
               v (Event Log)                               v (TCP :6380)                          v
+------------------------------+             +---------------------------+             +---------------------+
|        Apache Kafka          |             |       RCache Server       |             | Prometheus / Tempo  |
|  - document-ingestion-topic  |             |  - In-Memory Key-Value    |             |  & Grafana UI       |
|  - document-chunks-topic     |             |  - Custom TCP RCP Protocol|             +---------------------+
|  - ingestion-dlq             |             |  - Zero Redis Allowed     |
+--------------+---------------+             +---------------------------+
               |
               v (Consume Ingestion)
+----------------------------------------------------+
|       Python Ray Ingestion Cluster (Phase 5)       |
|  - Ray Tasks: Document Parsing (PDF, DOCX, OCR)   |
|  - Ray Actors: Tokenizers, Chunk Normalization     |
|  - Ray Data: Large Batch Chunk Stream Processing   |
+----------------------+-----------------------------+
                       |
                       v (Publish Chunks / Embeddings - Phase 6)
+----------------------------------------------------+             +------------------------------------+
|            PostgreSQL 16 + pgvector                |             |       Python Query Service         |
|  - Durable Documents & Version State Tables        | <---------> |  - Haystack Query Pipeline         |
|  - Baseline HNSW Index: vector(768)                |             |  - Sparse BM25 + Dense RRF Fusion  |
|  - chunk_embeddings Multi-Space Table (Phase 6)    |             |  - Cross-Encoder Neural Reranking  |
|  - GIN Index (Full-Text Search tsvector)           |             |  (LlamaIndex Benchmark in Phase 4) |
+----------------------------------------------------+             +-----------------+------------------+
                       ^                                                             ^
                       |                                                             |
                       +----------------- Spring Boot Retrieval Fallback ------------+
```

---

## 3. Component Responsibilities & Data Ownership

| Component | Technology | Primary Responsibilities | Data Owned |
| :--- | :--- | :--- | :--- |
| **Spring Boot Backend** | Java 21, Spring Boot 3.3+, LangChain4j | Public REST/SSE APIs, auth, tenant isolation, transactional outbox, generation gateway, prompt assembly, citation mapping, RCache client. | Tenant, user, document lifecycle, outbox events, chat session state. |
| **Python Ray Ingestion** | Python 3.11+, Ray Core & Data | Distributed CPU-bound document parsing, text normalization, OCR, structure-aware chunking. | Ephemeral worker staging, parsing intermediate datasets. |
| **Python Query Service** | Python 3.11+, Haystack (LlamaIndex alt) | Multi-branch retrieval, Reciprocal Rank Fusion, cross-encoder neural reranking. Returns structured evidence to Spring Boot. | Retrieval topology configurations, pipeline metrics. |
| **PostgreSQL 16** | PostgreSQL 16 + `uuid-ossp` | System of record for tenants, users, documents, versions, chunks, conversation history, and evaluation runs. | Relational application tables, audit logs, outbox table. |
| **pgvector** | PostgreSQL Extension | Dense vector indexing (HNSW `vector_cosine_ops`), vector distance search (`<=>`), multi-space representation tables. | Dense vector embeddings (`vector(768)` baseline, `chunk_embeddings`). |
| **RCache Server** | Custom Java 21 NIO Daemon | Sub-millisecond caching of exact-match query responses, computed embeddings, session histories, and rate-limiting counters. | Ephemeral key-value cache entries, TTL expiration wheels. |
| **Apache Kafka** | Kafka 3.7+ (KRaft) | Asynchronous ingestion pipeline event streaming, document chunking events, dead-letter queue (DLQ). | Ingestion event streams, processing payloads, retry logs. |
| **Ollama / Cloud LLM** | Ollama Engine / Cloud Providers | Neural inference for chat completion, token streaming, and embedding generation. | Neural weights, model artifacts, local GPU/CPU execution runtime. |
| **Next.js Frontend** | Next.js 14, React, Tailwind, TypeScript | Interactive UI for document upload, knowledge exploration, real-time streaming chat, and evaluation dashboards. | Client UI state, user input, token stream rendering. |

---

## 4. Synchronous vs Asynchronous Operations

### 4.1 Synchronous Operations (Request-Response / Streaming)
1. **RAG Query Execution (`POST /api/v1/query`)**:
   - Client sends query $\rightarrow$ Spring Boot checks RCache (Sync, $< 5\text{ms}$) $\rightarrow$ If hit, return cached answer.
   - If miss $\rightarrow$ Spring Boot calls Python Haystack Query Service for structured evidence set (Sync internal HTTP/gRPC, $\approx 30\text{ms}$) [Fallback: direct pgvector + BM25 query in Spring Boot if Python unavailable].
   - Spring Boot constructs grounded prompt with citation tokens $\rightarrow$ Invokes LLM via LangChain4j / Ollama $\rightarrow$ Emits tokens via SSE stream ($500\text{ms}-2000\text{ms}$).
   - Complete assembled answer is cached asynchronously in RCache $\rightarrow$ Stream closed.
2. **Document Metadata & Status Lookup (`GET /api/v1/documents/{id}`)**: Immediate PostgreSQL query.
3. **Phase 4 Retrieval Evaluation Benchmark (`POST /api/v1/evaluations/run-retrieval`)**: Synchronous offline benchmark execution against fixed golden fixtures.

### 4.2 Asynchronous Operations (Event-Driven / Kafka)
1. **Document Ingestion (`POST /api/v1/documents/upload`)**:
   - Binary validated and stored in `BlobStorageService` $\rightarrow$ `DocumentEntity` and `DocumentVersionEntity` persisted in PostgreSQL in `UPLOADED` state.
   - Outbox event written in same transaction $\rightarrow$ Outbox relay emits to Kafka `raglab.document.ingestion.v1` $\rightarrow$ Returns `202 Accepted` with `documentId` and `versionNumber`.
2. **Ray Distributed Processing (Phase 5)**:
   - Python Ray consumers pull message $\rightarrow$ Parse document into text $\rightarrow$ Generate deterministic chunks $\rightarrow$ Emit chunk batch to Kafka `raglab.document.chunks.v1`.
3. **Embedding & Indexing Pipeline (Phase 6)**:
   - Embedding worker batch-generates vectors $\rightarrow$ Writes chunks and vectors to PostgreSQL $\rightarrow$ Atomically cutover version to `READY` state.

---

## 5. Failure Behavior & Resilience Matrix

| Failed Component | Immediate Impact | System Reaction & Recovery Strategy | User Experience |
| :--- | :--- | :--- | :--- |
| **RCache Server** (Down / Unreachable) | Caching disabled. Query responses and embeddings cannot be read/written from cache. | Circuit breaker trips to `OPEN` state. All requests bypass cache and query PostgreSQL / Ollama directly. Background health check probes RCache periodically to close circuit. | Normal RAG functionality continues. Slight latency increase ($+5\text{ms}-20\text{ms}$) for repeated queries. |
| **Python Query Service** (Down / Timeout) | Advanced hybrid RRF & reranking pipeline temporarily unavailable. | Spring Boot circuit breaker trips to fallback mode: directly queries PostgreSQL using built-in Phase 2 native pgvector cosine similarity search (`findSimilarChunks`). | Search returns with basic dense vector recall; service does not fail. |
| **PostgreSQL** (Down / Unreachable) | Primary data & vector store unavailable. Ingestion cannot write; retrieval cannot fetch chunks. | Read queries fail with `503 Service Unavailable`. Ingestion consumer pauses Kafka offset commits to prevent data loss until DB recovers. | Error message: "Knowledge base temporarily unavailable. Please retry shortly." |
| **Apache Kafka** (Down / Unreachable) | Ingestion events cannot be produced or consumed. | Document upload writes to PostgreSQL transactional outbox. Uploads succeed with `202 Accepted`; outbox relay buffers events until Kafka recovers. Existing indexed documents remain fully searchable. | Document uploads accepted normally; ingestion processing resumes once Kafka reconnects. |
| **Ollama / LLM Gateway** (Down / Timeout) | LLM completions and local embeddings fail. | Gateway triggers retry with exponential backoff. If exhausted, fallback to configured secondary LLM provider or return structured degradation error. | Error message: "Inference engine timeout. Model is currently reloading or overloaded." |

---

## 6. Observability & Telemetry Architecture

- **OpenTelemetry SDK**: Standard distributed tracing across Spring Boot, Python Ray tasks, Python Haystack service, and RCache commands using W3C `traceparent` propagation.
- **Prometheus Metrics**:
  - `raglab_query_duration_seconds{status, strategy}`
  - `raglab_vector_search_latency_seconds{space_id, index_type}`
  - `raglab_rcache_requests_total{command, result="hit|miss|error"}`
  - `raglab_llm_time_to_first_token_seconds{model}`
  - `raglab_kafka_consumer_lag{topic, partition}`
  - `raglab_ingestion_stage_duration_seconds{stage}`
- **Health Indicators**: Spring Boot Actuator (`/actuator/health`) checks PostgreSQL, Kafka broker, and RCache TCP connectivity.
