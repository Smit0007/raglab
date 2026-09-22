# RAGLab: System Architecture Specification

## 1. Architectural Philosophy & Strategy

RAGLab is designed under four core architectural pillars:
1. **Modular Monolith First**: The application domain is encapsulated into high-cohesion, low-coupling modules inside a single Spring Boot application codebase. This eliminates distributed system overhead, network serialization costs, and complex orchestration during initial development while maintaining clean package boundaries that can be split into microservices if scaling demands dictate.
2. **Independent Datastore Engineering (RCache)**: RCache is treated as a separate standalone server daemon with its own custom client driver (`rcache-client`). RAGLab interacts with RCache over raw TCP socket connections using our proprietary protocol.
3. **Fail-Open Caching & Graceful Degradation**: The cache is purely an optimization layer. Any failure in RCache (network timeout, crash, connection drop) is caught by circuit breakers, immediately bypassed, and logged, allowing queries to be answered directly from the primary database (PostgreSQL) and LLM engine (Ollama).
4. **Asynchronous Ingestion Decoupling**: Heavy document parsing, chunking, and embedding generation are completely offloaded to Kafka event streams, ensuring HTTP ingestion endpoints respond instantly with job tracking identifiers.

---

## 2. High-Level System Architecture

```
                                      +------------------------------------+
                                      |         Next.js 14 Frontend        |
                                      |        (React / TypeScript)        |
                                      +-----------------+------------------+
                                                        |
                                           HTTPS / REST | SSE (Streaming)
                                                        v
+---------------------------------------------------------------------------------------------------------------+
|                                      RAGLab Backend (Spring Boot 3.x / Java 21)                                |
|                                                                                                               |
|  +-----------------------+     +-----------------------+     +---------------------+     +-----------------+  |
|  |   Ingestion Module    |     |   Retrieval Module    |     |  Generation Module  |     | Evaluation Mod  |  |
|  | - Doc Parsers (PDF..) |     | - Dense Vector Search |     | - Prompt Builder    |     | - RAG Triad     |  |
|  | - Text Chunkers       |     | - Lexical BM25 Search |     | - LangChain4j Gate  |     | - Benchmark Run |  |
|  | - Kafka Event Pub     |     | - RRF Hybrid Fusion   |     | - Citation Resolver |     | - Metric Logs   |  |
|  | - Status Tracker      |     | - Cross-Reranker      |     | - SSE Streamer      |     |                 |  |
|  +-----------+-----------+     +-----------+-----------+     +----------+----------+     +--------+--------+  |
|              |                             |                            |                         |           |
|              |                             +-------------+--------------+                         |           |
|              v                                           v                                        v           |
|  +-----------------------+                 +---------------------------+             +---------------------+  |
|  | Kafka Consumer Engine |                 |      RCache Client        |             |  Telemetry / Traces |  |
|  | - Chunk Embedder      |                 | - Custom Binary Protocol  |             |  OpenTelemetry SDK  |  |
|  | - Batch Vector Writer |                 | - Circuit Breaker / Pool  |             +----------+----------+  |
|  +-----------+-----------+                 +-------------+-------------+                        |             |
+--------------|-------------------------------------------|--------------------------------------|-------------+
               |                                           |                                      |
               v                                           v                                      v
+------------------------------+             +---------------------------+             +---------------------+
|        Apache Kafka          |             |       RCache Server       |             | Prometheus / Tempo  |
|  - document-ingestion-topic  |             |  - In-Memory Key-Value    |             |  & Grafana UI       |
|  - document-chunks-topic     |             |  - Single-Threaded Event  |             +---------------------+
|  - ingestion-dlq             |             |  - Custom TCP Protocol    |
+------------------------------+             +---------------------------+
               |
               v (writes chunks & vectors)
+----------------------------------------------------+             +------------------------------------+
|            PostgreSQL 16 + pgvector                |             |            Ollama Engine           |
|  - Relational Schema (docs, chunks, jobs, evals)   |             |  - Local LLM (Llama 3 / Mistral)   |
|  - HNSW Index (embedding vectors)                  | <---------> |  - Local Embeddings (Nomic / bge)  |
|  - GIN Index (Full-Text Search tsvector)           |             |  - Pluggable Cloud LLM Gateways    |
+----------------------------------------------------+             +------------------------------------+
```

---

## 3. Component Responsibilities & Data Ownership

| Component | Technology | Primary Responsibilities | Data Owned |
| :--- | :--- | :--- | :--- |
| **Spring Boot Backend** | Java 21, Spring Boot 3.3+, LangChain4j | API routing, business workflows, orchestration, RAG prompt assembly, citation mapping, evaluation runners. | Business logic state, request lifecycle, application configurations. |
| **RCache Server** | Custom In-Memory Store (Zero Redis) | Low-latency caching of query results, raw embedding vectors, session contexts, and rate limiter counters. | Ephemeral query caches, pre-computed embedding caches, rate limits. |
| **PostgreSQL** | PostgreSQL 16 | Relational persistence, transactional consistency, audit logs, document metadata, chunk records, evaluation metrics. | Document metadata, chunks, tenants, users, job statuses, experiment results. |
| **pgvector** | PostgreSQL Extension | High-dimensional vector indexing (HNSW / IVFFlat), vector distance calculation (Cosine, L2, Dot Product). | Dense vector embeddings ($1536$-dim or $768$-dim floats) associated with chunks. |
| **Apache Kafka** | Kafka 3.7+ (KRaft) | Asynchronous ingestion pipeline event streaming, document chunking events, batch embedding distribution, DLQ. | Ingestion event streams, processing job payloads, retry events. |
| **Ollama** | Local LLM Server | Local neural inference for chat completion, streaming generation, and embedding vector calculation. | Neural weights, model artifacts, local GPU/CPU execution runtime. |
| **Next.js Frontend** | Next.js 14, React, Tailwind, TypeScript | Interactive UI for document upload, knowledge exploration, real-time streaming chat, and evaluation dashboards. | Client UI state, user input, token stream rendering. |

---

## 4. Synchronous vs Asynchronous Operations

### 4.1 Synchronous Operations (Request-Response / Streaming)
1. **RAG Query Execution (`POST /api/v1/query`)**:
   - Client sends query $\rightarrow$ Backend checks RCache (Sync, $< 5\text{ms}$) $\rightarrow$ If hit, return cached answer.
   - If miss $\rightarrow$ Backend invokes Embedding Engine (Sync, $\approx 30\text{ms}$) $\rightarrow$ Query PostgreSQL/pgvector for Top-K chunks (Sync, $\approx 20\text{ms}$) $\rightarrow$ Context builder & Prompt construction $\rightarrow$ LLM generation / SSE stream (Sync Stream, $500\text{ms}-2000\text{ms}$) $\rightarrow$ Cache response asynchronously in RCache $\rightarrow$ Complete.
2. **Document Metadata & Status Lookup (`GET /api/v1/documents/{id}`)**:
   - Queries PostgreSQL metadata tables directly with immediate response.
3. **Evaluation Metric Trigger (`POST /api/v1/evaluations/run-single`)**:
   - Real-time scoring of a single query-context-response triad.

### 4.2 Asynchronous Operations (Event-Driven / Kafka)
1. **Document Ingestion (`POST /api/v1/documents/upload`)**:
   - Client uploads document $\rightarrow$ Backend stores raw binary in storage $\rightarrow$ Creates `PENDING` record in PostgreSQL $\rightarrow$ Publishes `DocumentIngestionEvent` to Kafka $\rightarrow$ Returns `202 Accepted` with `jobId`.
2. **Chunking & Vector Ingestion (Kafka Consumer)**:
   - Consumer reads `DocumentIngestionEvent` $\rightarrow$ Parses and chunks text $\rightarrow$ Generates batch embeddings via Ollama/LangChain4j $\rightarrow$ Bulk writes chunks & vectors to PostgreSQL `document_chunks` table $\rightarrow$ Updates document status to `INDEXED` $\rightarrow$ Evicts stale tenant caches in RCache.
3. **Batch Evaluation Harness (`POST /api/v1/evaluations/batch`)**:
   - Dispatches batch test cases to Kafka $\rightarrow$ Worker consumers execute retrieval and generation variations $\rightarrow$ Scores metrics $\rightarrow$ Writes aggregate results to PostgreSQL.

---

## 5. Failure Behavior & Resilience Matrix

| Failed Component | Immediate Impact | System Reaction & Recovery Strategy | User Experience |
| :--- | :--- | :--- | :--- |
| **RCache Server** (Down / Unreachable) | Caching disabled. Query responses and embeddings cannot be read/written from cache. | Circuit breaker trips to `OPEN` state. All requests bypass cache and query PostgreSQL / Ollama directly. Background health check probes RCache periodically to close circuit. | Normal RAG functionality continues. Slight latency increase ($+5\text{ms}-20\text{ms}$) for repeated queries. |
| **PostgreSQL** (Down / Unreachable) | Primary data & vector store unavailable. Ingestion cannot write; retrieval cannot fetch chunks. | Read queries fail with `503 Service Unavailable`. Ingestion consumer pauses Kafka offset commits to prevent data loss until DB recovers. | Error message: "Knowledge base temporarily unavailable. Please retry shortly." |
| **Apache Kafka** (Down / Unreachable) | Ingestion events cannot be produced or consumed. | `POST /api/v1/documents/upload` fails gracefully with `503 Service Unavailable` or buffers temporarily in a local transactional outbox. Existing indexed documents remain fully searchable. | Document uploads temporarily disabled; Query and Search work normally. |
| **Ollama / LLM Gateway** (Down / Timeout) | LLM completions and local embeddings fail. | Gateway triggers retry with exponential backoff. If exhausted, fallback to configured secondary LLM provider or return structured degradation error. | Error message: "Inference engine timeout. Model is currently reloading or overloaded." |

---

## 6. Observability & Telemetry Architecture

- **OpenTelemetry SDK**: Automatically instruments incoming HTTP requests, Spring Web MVC controllers, JDBC connections (PostgreSQL), Kafka Producers/Consumers, Ollama HTTP clients, and RCache TCP commands.
- **Distributed Trace Context Propagation**: Every incoming query assigns a W3C `traceparent` header. Traces flow through:
  `Client -> Spring Boot -> [RCache Client | PostgreSQL pgvector | Ollama API]`
- **Prometheus Metrics**:
  - `raglab_query_duration_seconds{status, strategy}`
  - `raglab_vector_search_latency_seconds{index_type}`
  - `raglab_rcache_requests_total{command, result="hit|miss|error"}`
  - `raglab_llm_time_to_first_token_seconds{model}`
  - `raglab_kafka_consumer_lag{topic, partition}`
  - `raglab_evaluation_score{metric="faithfulness|relevance|groundedness"}`
- **Grafana Dashboards**: Unified view showing real-time RAG latency breakdowns, LLM token throughput, vector search efficiency, and cache utilization.
